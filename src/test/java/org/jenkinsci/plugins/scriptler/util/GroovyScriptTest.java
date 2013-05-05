package org.jenkinsci.plugins.scriptler.util;

import hudson.util.StreamTaskListener;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.junit.Test;

public class GroovyScriptTest {

    @Test
    public void scriptReturnFalse() {
        ByteArrayOutputStream sos = new ByteArrayOutputStream();
        GroovyScript gs = new GroovyScript("return false", new Parameter[0], true, new StreamTaskListener(sos)) {
            @Override
            public ClassLoader getClassLoader() {
                return Thread.currentThread().getContextClassLoader();
            }
        };
        Object result = gs.call();
        assertTrue(sos.toString().contains("Result:   false"));
        assertEquals(false, result);
    }

    @Test
    public void scriptReturnTrue() {
        ByteArrayOutputStream sos = new ByteArrayOutputStream();
        GroovyScript gs = new GroovyScript("return true", new Parameter[0], true, new StreamTaskListener(sos)) {
            @Override
            public ClassLoader getClassLoader() {
                return Thread.currentThread().getContextClassLoader();
            }
        };
        Object result = gs.call();
        assertTrue(sos.toString().contains("Result:   true"));
        assertEquals(true, result);
    }

    @Test
    public void helloWorld() {
        ByteArrayOutputStream sos = new ByteArrayOutputStream();
        GroovyScript gs = new GroovyScript("out.print(\"HelloWorld\")", new Parameter[0], true, new StreamTaskListener(sos)) {
            @Override
            public ClassLoader getClassLoader() {
                return Thread.currentThread().getContextClassLoader();
            }
        };
        Object result = gs.call();
        assertEquals("HelloWorld", sos.toString());
        assertEquals("", result);
    }
}
