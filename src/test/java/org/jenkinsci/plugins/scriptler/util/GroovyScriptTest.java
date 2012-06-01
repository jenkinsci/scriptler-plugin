package org.jenkinsci.plugins.scriptler.util;

import static org.junit.Assert.assertEquals;

import java.io.PrintWriter;

import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.junit.Test;

public class GroovyScriptTest {

    @Test
    public void scriptReturnFalse() {
        GroovyScript gs = new GroovyScript("return false", new Parameter[0], true, new PrintWriter(System.out)) {
            @Override
            public ClassLoader getClassLoader() {
                return Thread.currentThread().getContextClassLoader();
            }
        };
        Object result = gs.call();
        assertEquals(false, result);
    }

    @Test
    public void scriptReturnTrue() {
        GroovyScript gs = new GroovyScript("return true", new Parameter[0], true, new PrintWriter(System.out)) {
            @Override
            public ClassLoader getClassLoader() {
                return Thread.currentThread().getContextClassLoader();
            }
        };
        Object result = gs.call();
        assertEquals(true, result);
    }

    @Test
    public void helloWorld() {
        GroovyScript gs = new GroovyScript("out.print(\"HelloWorld\")", new Parameter[0], true, new PrintWriter(System.out)) {
            @Override
            public ClassLoader getClassLoader() {
                return Thread.currentThread().getContextClassLoader();
            }
        };
        Object result = gs.call();
        assertEquals("", result);
    }
}
