/*
 * Copyright (c) 2003-2005 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * - Redistribution of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * 
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 * 
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN
 * MICROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR
 * ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR
 * DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
 * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
 * ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF
 * SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 * 
 * You acknowledge that this software is not designed or intended for use
 * in the design, construction, operation or maintenance of any nuclear
 * facility.
 * 
 * Sun gratefully acknowledges that this software was originally authored
 * and developed by Kenneth Bradley Russell and Christopher John Kline.
 */

package com.sun.gluegen.opengl;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import com.sun.gluegen.*;
import com.sun.gluegen.cgram.types.*;
import com.sun.gluegen.procaddress.*;
import com.sun.gluegen.runtime.opengl.GLExtensionNames;

/**
 * A subclass of ProcAddressEmitter with special OpenGL-specific
 * configuration abilities.
 */
public class GLEmitter extends ProcAddressEmitter {

  // Keeps track of which MethodBindings were created for handling
  // Buffer Object variants. Used as a Set rather than a Map.
  private Map<MethodBinding, MethodBinding> bufferObjectMethodBindings = new IdentityHashMap<MethodBinding, MethodBinding>();

  static class BufferObjectKind {

    private BufferObjectKind() {}

    static final BufferObjectKind UNPACK_PIXEL = new BufferObjectKind();
    static final BufferObjectKind PACK_PIXEL   = new BufferObjectKind();
    static final BufferObjectKind ARRAY        = new BufferObjectKind();
    static final BufferObjectKind ELEMENT      = new BufferObjectKind();
  }
  
  @Override
  public void beginEmission(GlueEmitterControls controls) throws IOException  {
    getGLConfig().parseGLHeaders(controls);
    renameExtensionsIntoCore();
    if (getGLConfig().getAutoUnifyExtensions()) {
      unifyExtensions(controls);
    }
    super.beginEmission(controls);
  }

  protected void renameExtensionsIntoCore() {
    // This method handles renaming of entire extensions into the
    // OpenGL core namespace. For example, it is used to move certain
    // OpenGL ES (OES) extensions into the core namespace which are
    // already in the core namespace in desktop OpenGL. It builds upon
    // renaming mechanisms that are built elsewhere.
    
    GLConfiguration config = getGLConfig();
    Set<String> extensionsRenamedIntoCore = config.getExtensionsRenamedIntoCore();
    BuildStaticGLInfo glInfo = config.getGLInfo();
    if(null==glInfo) {
        if(extensionsRenamedIntoCore.size()>0) {
            throw new RuntimeException("ExtensionRenamedIntoCore (num: "+extensionsRenamedIntoCore.size()+"), but no GLHeader");
        }
        return;
    }
    for (String extension : extensionsRenamedIntoCore) {
      Set<String> declarations = glInfo.getDeclarations(extension);
      if (declarations != null) {
        for (Iterator i2 = declarations.iterator(); i2.hasNext(); ) {
          String decl = (String) i2.next();
          boolean isGLFunction = GLExtensionNames.isGLFunction(decl);
          boolean isGLEnumeration = false;
          if (!isGLFunction) {
            isGLEnumeration = GLExtensionNames.isGLEnumeration(decl);
          }
          if (isGLFunction || isGLEnumeration) {
            String renamed = GLExtensionNames.normalize(decl, isGLFunction);
            if(!renamed.equals(decl)) {
                config.addJavaSymbolRename(decl, renamed);
            }
          }
        }
      }
    }
  }

  class ExtensionUnifier implements SymbolFilter {
      private List<ConstantDefinition> constants;
      private List<FunctionSymbol> functions;

      public void filterSymbols(List<ConstantDefinition> constants,
                                List<FunctionSymbol> functions) {
          this.constants = constants;
          this.functions = functions;
          doWork();
      }

      public List<ConstantDefinition> getConstants() {
          return constants;
      }
      
      public List<FunctionSymbol> getFunctions() {
          return functions;
      }

      private void doWork() {
          BuildStaticGLInfo glInfo = getGLConfig().getGLInfo();
          if (glInfo == null) {
              return;
          }
          // Try to retain a "good" ordering for these symbols
          Map<String, ConstantDefinition> constantMap = new LinkedHashMap();
          Map<String, FunctionSymbol> functionMap = new LinkedHashMap();
          for (Iterator iter = constants.iterator(); iter.hasNext(); ) {
              ConstantDefinition def = (ConstantDefinition) iter.next();
              constantMap.put(def.getName(), def);
          }
          for (Iterator iter = functions.iterator(); iter.hasNext(); ) {
              FunctionSymbol sym = (FunctionSymbol) iter.next();
              functionMap.put(sym.getName(), sym);
          }
          // Go through all of the declared extensions.
          // For each extension, look at its #define and function symbols.
          // If we find all of the extension's symbols in the core API under
          // non-ARB (or whatever is the suffix) names, then remove this extension
          // from the public API. If it turns out that we are running on hardware
          // that doesn't support the core version of these APIs, the runtime
          // will take care of looking up the extension version of these entry
          // points.
          Set<String> extensionNames = glInfo.getExtensions();
          for (Iterator iter1 = extensionNames.iterator(); iter1.hasNext(); ) {
              String extension = (String) iter1.next();
              Set<String> declarations = glInfo.getDeclarations(extension);
              boolean isExtension = true;
              boolean shouldUnify = true;
              String cause = null;
              for (Iterator iter2 = declarations.iterator(); iter2.hasNext(); ) {
                  String decl = (String) iter2.next();
                  boolean isFunc = !decl.startsWith("GL_");
                  if (!GLExtensionNames.isExtension(decl, isFunc)) {
                      isExtension = false;
                      break;
                  }
                  // See whether we're emitting glue code for this
                  // entry point or definition at all
                  if (isFunc) {
                      if (!functionMap.containsKey(decl)) {
                          isExtension = false;
                          break;
                      }
                  } else {
                      if (!constantMap.containsKey(decl)) {
                          isExtension = false;
                          break;
                      }
                  }
                  cause = decl;
                  String unifiedName = GLExtensionNames.normalize(decl, isFunc);
                  // NOTE that we look up the unified name in the
                  // BuildStaticGLInfo's notion of the APIs -- since
                  // we might not be emitting glue code for the
                  // headers that actually contain the core entry
                  // point. Think of the case where we are parsing the
                  // GLES2 gl2.h, which contains certain desktop
                  // OpenGL extensions that have been moved into the
                  // core, but later generating the implementing glue
                  // code (not the interface) for the desktop gl.h /
                  // glext.h.
                  shouldUnify = (glInfo.getExtension(unifiedName) != null);
                  //                  if (isFunc) {
                  //                      shouldUnify = functionMap.containsKey(unifiedName);
                  //                  } else {
                  //                      shouldUnify = constantMap.containsKey(unifiedName);
                  //                  }
                  if (!shouldUnify) {
                      break;
                  }
              }
              if (isExtension) {
                  if (shouldUnify) {
                      for (Iterator iter2 = declarations.iterator(); iter2.hasNext(); ) {
                          String decl = (String) iter2.next();
                          boolean isFunc = !decl.startsWith("GL_");
                          if (isFunc) {
                              functionMap.remove(decl);
                          } else {
                              constantMap.remove(decl);
                          }
                      }
                      System.err.println("INFO: unified extension " + extension + " into core API");
                  } else {
                      System.err.println("INFO: didn't unify extension " + extension + " into core API because of " + cause);
                  }
              }
          }
          constants = new ArrayList();
          for (Iterator iter = constantMap.keySet().iterator(); iter.hasNext(); ) {
              constants.add(constantMap.get(iter.next()));
          }
          functions = new ArrayList();
          for (Iterator iter = functionMap.keySet().iterator(); iter.hasNext(); ) {
              functions.add(functionMap.get(iter.next()));
          }
      }
  }

  private void unifyExtensions(GlueEmitterControls controls) {
      controls.runSymbolFilter(new ExtensionUnifier());
  }

  protected JavaConfiguration createConfig() {
    return new GLConfiguration(this);
  }

  /** In order to implement Buffer Object variants of certain
      functions we generate another MethodBinding which maps the void*
      argument to a Java long. The generation of emitters then takes
      place as usual. We do however need to keep track of the modified
      MethodBinding object so that we can also modify the emitters
      later to inform them that their argument has changed. We might
      want to push this functionality down into the MethodBinding
      (i.e., mutators for argument names). We also would need to
      inform the CMethodBindingEmitter that it is overloaded in this
      case (though we default to true currently). */
  protected List<MethodBinding> expandMethodBinding(MethodBinding binding) {
    List<MethodBinding> bindings = super.expandMethodBinding(binding);
    
    if (!getGLConfig().isBufferObjectFunction(binding.getName())) {
      return bindings;
    }

    List<MethodBinding> newBindings = new ArrayList();
    newBindings.addAll(bindings);

    // Need to expand each one of the generated bindings to take a
    // Java long instead of a Buffer for each void* argument
    for (Iterator iter = bindings.iterator(); iter.hasNext(); ) {
      MethodBinding cur = (MethodBinding) iter.next();
      
      // Some of these routines (glBitmap) take strongly-typed
      // primitive pointers as arguments which are expanded into
      // non-void* arguments
      // This test (rather than !signatureUsesNIO) is used to catch
      // more unexpected situations
      if (cur.signatureUsesJavaPrimitiveArrays()) {
        continue;
      }

      MethodBinding result = cur;
      for (int i = 0; i < cur.getNumArguments(); i++) {
        if (cur.getJavaArgumentType(i).isNIOBuffer()) {
          result = result.replaceJavaArgumentType(i, JavaType.createForClass(Long.TYPE));
        }
      }

      if (result == cur) {
        throw new RuntimeException("Error: didn't find any void* arguments for BufferObject function " +
                                   binding.getName());
      }

      newBindings.add(result);
      // Now need to flag this MethodBinding so that we generate the
      // correct flags in the emitters later
      bufferObjectMethodBindings.put(result, result);
    }

    return newBindings;
  }

  protected boolean needsModifiedEmitters(FunctionSymbol sym) {
    if ((!needsProcAddressWrapper(sym) && !needsBufferObjectVariant(sym)) ||
        getConfig().isUnimplemented(sym.getName())) {
      return false;
    }

    return true;
  }

  public boolean isBufferObjectMethodBinding(MethodBinding binding) {
    return bufferObjectMethodBindings.containsKey(binding);
  }

  public void emitDefine(ConstantDefinition def, String optionalComment) throws Exception {
      BuildStaticGLInfo glInfo = getGLConfig().getGLInfo();
      if(null==glInfo) {
        throw new Exception("No GLInfo for: "+def);
      }
      String symbolRenamed = def.getName();
      StringBuffer newComment = new StringBuffer();
      newComment.append("Part of <code>");
      if(0==addExtensionsOfSymbols2Buffer(newComment, ", ", symbolRenamed, def.getAliasedNames())) {
        if(def.isEnum()) {
          String enumName = def.getEnumName();
          if(null!=enumName) {
              newComment.append(enumName);
          } else {
              newComment.append("CORE ENUM");
          }
        } else {
          if(getGLConfig().getAllowNonGLExtensions()) {
              newComment.append("CORE DEF");
          } else {
              // Note: All GL defines must be contained within an extension marker !
              // #ifndef GL_EXT_lala
              // #define GL_EXT_lala 1
              // ...
              // #endif
              if(JavaConfiguration.DEBUG_IGNORES) {
                  StringBuffer sb = new StringBuffer();
                  JavaEmitter.addStrings2Buffer(sb, ", ", symbolRenamed, def.getAliasedNames());
                  System.err.println("Dropping marker: "+sb.toString());
              }
              return;
          }
        }
      }
      newComment.append("</code>");

      if(null!=optionalComment) {
        newComment.append("<br>");
        newComment.append(optionalComment);
      }

      super.emitDefine(def, newComment.toString());
  }

  public int addExtensionsOfSymbols2Buffer(StringBuffer buf, String sep, String first, Collection col) {
    BuildStaticGLInfo glInfo = getGLConfig().getGLInfo();
    if(null==glInfo) {
        throw new RuntimeException("No GLInfo for: "+first);
    }
    int num = 0;
    if(null==buf) buf=new StringBuffer();
    String extensionName;

    Iterator iter=col.iterator();
    if(null!=first) {
        extensionName = glInfo.getExtension(first);
        if(null!=extensionName) {
            buf.append(extensionName);
            if( iter.hasNext() ) {
                buf.append(sep);
            }
            num++;
        }
    }
    while( iter.hasNext() ) {
        extensionName = glInfo.getExtension((String)iter.next());
        if(null!=extensionName) {
            buf.append(extensionName);
            if( iter.hasNext() ) {
                buf.append(sep);
            }
            num++;
        }
    }
    return num;
  }

  //----------------------------------------------------------------------
  // Internals only below this point
  //

  protected void generateModifiedEmitters(JavaMethodBindingEmitter baseJavaEmitter, List emitters) {
    List superEmitters = new ArrayList();
    super.generateModifiedEmitters(baseJavaEmitter, superEmitters);

    // See whether this is one of the Buffer Object variants
    boolean bufferObjectVariant = bufferObjectMethodBindings.containsKey(baseJavaEmitter.getBinding());

    for (Iterator iter = superEmitters.iterator(); iter.hasNext(); ) {
        JavaMethodBindingEmitter emitter = (JavaMethodBindingEmitter) iter.next();
        if (emitter instanceof ProcAddressJavaMethodBindingEmitter) {
          emitter = new GLJavaMethodBindingEmitter((ProcAddressJavaMethodBindingEmitter) emitter, this, bufferObjectVariant);
        }
        emitters.add(emitter);
    }
  }

  protected boolean needsBufferObjectVariant(FunctionSymbol sym) {
    return getGLConfig().isBufferObjectFunction(sym.getName());
  }
  
  protected GLConfiguration getGLConfig() {
    return (GLConfiguration) getConfig();
  }

  protected void endProcAddressTable() throws Exception
  {
    PrintWriter w = tableWriter;

    w.println("  /**");
    w.println("   * This is a convenience method to get (by name) the native function");
    w.println("   * pointer for a given function. It lets you avoid having to");
    w.println("   * manually compute the &quot;" + PROCADDRESS_VAR_PREFIX + " + ");
    w.println("   * &lt;functionName&gt;&quot; member variable name and look it up via");
    w.println("   * reflection; it also will throw an exception if you try to get the");
    w.println("   * address of an unknown function, or one that is statically linked");
    w.println("   * and therefore does not have a function pointer in this table.");
    w.println("   *");
    w.println("   * @throws RuntimeException if the function pointer was not found in");
    w.println("   *   this table, either because the function was unknown or because");
    w.println("   *   it was statically linked.");
    w.println("   */");
    w.println("  public long getAddressFor(String functionNameUsr) {");
    w.println("    String functionNameBase = com.sun.gluegen.runtime.opengl.GLExtensionNames.normalizeVEN(com.sun.gluegen.runtime.opengl.GLExtensionNames.normalizeARB(functionNameUsr, true), true);");
    w.println("    String addressFieldNameBase = " + getProcAddressConfig().gluegenRuntimePackage() + ".ProcAddressHelper.PROCADDRESS_VAR_PREFIX + functionNameBase;");
    w.println("    java.lang.reflect.Field addressField = null;");
    w.println("    int  funcNamePermNum = com.sun.gluegen.runtime.opengl.GLExtensionNames.getFuncNamePermutationNumber(functionNameBase);");
    w.println("    for(int i = 0; null==addressField && i < funcNamePermNum; i++) {");
    w.println("        String addressFieldName = com.sun.gluegen.runtime.opengl.GLExtensionNames.getFuncNamePermutation(addressFieldNameBase, i);");
    w.println("        try {");
    w.println("          addressField = getClass().getField(addressFieldName);");
    w.println("        } catch (Exception e) { }");
    w.println("    }");
    w.println("");
    w.println("    if(null==addressField) {");
    w.println("      // The user is calling a bogus function or one which is not");
    w.println("      // runtime linked");
    w.println("      throw new RuntimeException(");
    w.println("          \"WARNING: Address field query failed for \\\"\" + functionNameBase + \"\\\"/\\\"\" + functionNameUsr +");
    w.println("          \"\\\"; it's either statically linked or address field is not a known \" +");
    w.println("          \"function\");");
    w.println("    } ");
    w.println("    try {");
    w.println("      return addressField.getLong(this);");
    w.println("    } catch (Exception e) {");
    w.println("      throw new RuntimeException(");
    w.println("          \"WARNING: Address query failed for \\\"\" + functionNameBase + \"\\\"/\\\"\" + functionNameUsr +");
    w.println("          \"\\\"; it's either statically linked or is not a known \" +");
    w.println("          \"function\", e);");
    w.println("    }");
    w.println("  }");

    w.println("} // end of class " + tableClassName);
    w.flush();
    w.close();
  }
}
