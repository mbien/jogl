/*
 * Copyright (c) 2003 Sun Microsystems, Inc. All Rights Reserved.
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
 */

package com.sun.opengl.impl.awt;

import com.sun.opengl.impl.*;

import javax.media.opengl.*;

import java.lang.reflect.*;
import java.awt.GraphicsEnvironment;

public class AWTUtil {
  // See whether we're running in headless mode
  private static boolean headlessMode;
  private static Class j2dClazz = null;
  private static Method isOGLPipelineActive = null;
  private static Method isQueueFlusherThread = null;
  private static boolean j2dOk = false;

  static {
    lockedToolkit = false;
    headlessMode = GraphicsEnvironment.isHeadless();
    try {
        j2dClazz = Class.forName("com.sun.opengl.impl.j2d.Java2D");
        isOGLPipelineActive = j2dClazz.getMethod("isOGLPipelineActive", null);
        isQueueFlusherThread = j2dClazz.getMethod("isQueueFlusherThread", null);
        j2dOk = true;
    } catch (Exception e) {}
  }

  private static boolean lockedToolkit;

  public static void lockToolkit() throws GLException {
    if (lockedToolkit) {
      throw new GLException("Toolkit already locked");
    }
    lockedToolkit = true;

    if (headlessMode) {
      // Workaround for running (to some degree) in headless
      // environments but still supporting rendering via pbuffers
      // For full correctness, would need to implement a Lock class
      return;
    }

    if(j2dOk) {
      try {
        if( !((Boolean)isOGLPipelineActive.invoke(null, null)).booleanValue() ||
            !((Boolean)isQueueFlusherThread.invoke(null, null)).booleanValue() ) {
          JAWT.getJAWT().Lock();
        }
      } catch (Exception e) { j2dOk=false; }
    }
    if(!j2dOk) {
      JAWT.getJAWT().Lock();
    }
  }

  public static void unlockToolkit() {
    if (lockedToolkit) {
        if (headlessMode) {
          // Workaround for running (to some degree) in headless
          // environments but still supporting rendering via pbuffers
          // For full correctness, would need to implement a Lock class
          return;
        }

        if(j2dOk) {
          try {
            if( !((Boolean)isOGLPipelineActive.invoke(null, null)).booleanValue() ||
                !((Boolean)isQueueFlusherThread.invoke(null, null)).booleanValue() ) {
              JAWT.getJAWT().Unlock();
            }
          } catch (Exception e) { j2dOk=false; }
        } 
        if(!j2dOk) {
          JAWT.getJAWT().Unlock();
        }
        lockedToolkit = false;
    }
  }

  public static boolean isToolkitLocked() {
    return lockedToolkit;
  }

}