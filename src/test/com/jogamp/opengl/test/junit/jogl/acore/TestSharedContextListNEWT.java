/**
 * Copyright 2010 JogAmp Community. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 * 
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 * 
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY JogAmp Community ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JogAmp Community OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of JogAmp Community.
 */
 
package com.jogamp.opengl.test.junit.jogl.acore;

import com.jogamp.newt.opengl.GLWindow;

import javax.media.opengl.FPSCounter;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLDrawableFactory;
import javax.media.opengl.GLPbuffer;
import javax.media.opengl.GLProfile;
import com.jogamp.opengl.util.Animator;

import com.jogamp.opengl.test.junit.util.UITestCase;
import com.jogamp.opengl.test.junit.jogl.demos.gl2.gears.Gears;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestSharedContextListNEWT extends UITestCase {
    static GLProfile glp;
    static GLCapabilities caps;
    static int width, height;
    GLPbuffer sharedDrawable;
    Gears sharedGears;

    @BeforeClass
    public static void initClass() {
        GLProfile.initSingleton(true);
        glp = GLProfile.getDefault();
        Assert.assertNotNull(glp);
        caps = new GLCapabilities(glp);
        Assert.assertNotNull(caps);
        width  = 512;
        height = 512;
    }

    private void initShared() {
        sharedDrawable = GLDrawableFactory.getFactory(glp).createGLPbuffer(null, caps, null, width, height, null);
        Assert.assertNotNull(sharedDrawable);
        sharedGears = new Gears();
        Assert.assertNotNull(sharedGears);
        sharedDrawable.addGLEventListener(sharedGears);
        // init and render one frame, which will setup the Gears display lists
        sharedDrawable.display();
    }

    private void releaseShared() {
        Assert.assertNotNull(sharedDrawable);
        sharedDrawable.destroy();
    }

    protected GLWindow runTestGL(Animator animator, int x, int y, boolean useShared) {
        GLWindow glWindow = GLWindow.create(caps);
        Assert.assertNotNull(glWindow);
        glWindow.setTitle("Shared Gears NEWT Test: "+x+"/"+y+" shared "+useShared);
        if(useShared) {
            glWindow.setSharedContext(sharedDrawable.getContext());
        }

        glWindow.setSize(width, height);
        glWindow.setPosition(x, y);

        Gears gears = new Gears();
        if(useShared) {
            gears.setGears(sharedGears.getGear1(), sharedGears.getGear2(), sharedGears.getGear3());
        }
        glWindow.addGLEventListener(gears);

        animator.add(glWindow);

        glWindow.setVisible(true);

        return glWindow;
    }

    @Test
    public void test01() throws InterruptedException {
        initShared();
        Animator animator = new Animator();
        GLWindow f1 = runTestGL(animator, 0, 0, true);
        GLWindow f2 = runTestGL(animator, width, 0, true);
        GLWindow f3 = runTestGL(animator, 0, height, false);
        animator.setUpdateFPSFrames(1, null);        
        animator.start();
        while(animator.isAnimating() && animator.getTotalFPSDuration()<duration) {
            Thread.sleep(100);
        }
        animator.stop();

        // here we go again: On AMD/X11 the create/destroy sequence must be the same
        // even though this is agains the chicken/egg logic here ..
        releaseShared();

        f1.destroy();
        f2.destroy();
        f3.destroy();

        // see above ..
        // releaseShared();
    }

    static long duration = 500; // ms

    public static void main(String args[]) {
        for(int i=0; i<args.length; i++) {
            if(args[i].equals("-time")) {
                i++;
                try {
                    duration = Integer.parseInt(args[i]);
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        }
        org.junit.runner.JUnitCore.main(TestSharedContextListNEWT.class.getName());
    }
}
