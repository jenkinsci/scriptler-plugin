package org.jenkinsci.plugins.scriptler.util;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.junit.Test;

public class GroovyScriptTest {

    @Test
    public void scriptReturnFalse() {
        ByteArrayOutputStream sos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(sos);
        GroovyScript gs = new GroovyScript("return false", new Parameter[0], true, ps) {
            @Override
            public ClassLoader getClassLoader() {
                return Thread.currentThread().getContextClassLoader();
            }
        };
        Object result = gs.call();
        assertEquals("", sos.toString());
        assertEquals(false, result);
    }

    @Test
    public void scriptReturnTrue() {
        ByteArrayOutputStream sos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(sos);
        GroovyScript gs = new GroovyScript("return true", new Parameter[0], true, System.out) {
            @Override
            public ClassLoader getClassLoader() {
                return Thread.currentThread().getContextClassLoader();
            }
        };
        Object result = gs.call();
        assertEquals("", sos.toString());
        assertEquals(true, result);
    }

    @Test
    public void helloWorld() {
        ByteArrayOutputStream sos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(sos);
        GroovyScript gs = new GroovyScript("out.print(\"HelloWorld\")", new Parameter[0], true, ps) {
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
