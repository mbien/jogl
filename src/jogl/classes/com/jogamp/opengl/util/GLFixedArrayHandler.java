
package com.jogamp.opengl.util;

import javax.media.opengl.*;
import javax.media.opengl.fixedfunc.*;
import com.jogamp.opengl.util.*;
import java.nio.*;

public class GLFixedArrayHandler implements GLArrayHandler {
  private GLArrayDataEditable ad;

  public GLFixedArrayHandler(GLArrayDataEditable ad) {
    this.ad = ad;
  }

  private final void passArrayPointer(GLPointerFunc gl) {
    switch(ad.getIndex()) {
        case GLPointerFunc.GL_VERTEX_ARRAY:
            gl.glVertexPointer(ad);
            break;
        case GLPointerFunc.GL_NORMAL_ARRAY:
            gl.glNormalPointer(ad);
            break;
        case GLPointerFunc.GL_COLOR_ARRAY:
            gl.glColorPointer(ad);
            break;
        case GLPointerFunc.GL_TEXTURE_COORD_ARRAY:
            gl.glTexCoordPointer(ad);
            break;
        default:
            throw new GLException("invalid glArrayIndex: "+ad.getIndex()+":\n\t"+ad); 
    }
  }

  public void enableBuffer(GL gl, boolean enable) {
    GLPointerFunc glp = gl.getGL2ES1();
    if(enable) {
        glp.glEnableClientState(ad.getIndex());

        Buffer buffer = ad.getBuffer();

        if(ad.isVBO()) {
            // always bind and refresh the VBO mgr, 
            // in case more than one gl*Pointer objects are in use
            gl.glBindBuffer(ad.getVBOTarget(), ad.getVBOName());
            if(!ad.isVBOWritten()) {
                if(null!=buffer) {
                    gl.glBufferData(ad.getVBOTarget(), buffer.limit() * ad.getComponentSize(), buffer, ad.getVBOUsage());
                }
                ad.setVBOWritten(true);
            }
            passArrayPointer(glp);
        } else if(null!=buffer) {
            passArrayPointer(glp);
            ad.setVBOWritten(true);
        }
    } else {
        if(ad.isVBO()) {
            gl.glBindBuffer(ad.getVBOTarget(), 0);
        }
        glp.glDisableClientState(ad.getIndex());
    }
  }
}

