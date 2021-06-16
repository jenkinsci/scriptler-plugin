package org.jenkinsci.plugins.scriptler.util;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import hudson.util.StreamTaskListener;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

public class GroovyScriptTest {

    @Rule
    public ErrorCollector errorCollector = new ErrorCollector();

    @Test
    public void scriptReturnFalse() {
        ByteArrayOutputStream sos = new ByteArrayOutputStream();
        GroovyScript gs = newInstance(sos, "return false");
        Object result = gs.call();
        assertTrue(sos.toString().contains("Result:   false"));
        assertEquals(false, result);
    }

    @Test
    public void scriptReturnTrue() {
        ByteArrayOutputStream sos = new ByteArrayOutputStream();
        GroovyScript gs = newInstance(sos, "return true");
        Object result = gs.call();
        assertTrue(sos.toString().contains("Result:   true"));
        assertEquals(true, result);
    }

    @Test
    public void helloWorld() {
        ByteArrayOutputStream sos = new ByteArrayOutputStream();
        GroovyScript gs = newInstance(sos, "out.print(\"HelloWorld\")");
        Object result = gs.call();
        assertEquals("HelloWorld", sos.toString());
        assertEquals("", result);
    }

    @Test
    public void repeatedInvocation() {
        ByteArrayOutputStream sos = new ByteArrayOutputStream();

        newInstance(sos, "out.print arg", new Parameter("arg", "firstOne")).call();
        assertEquals("firstOne", sos.toString());

        sos.reset();
        newInstance(sos, "out.print arg", new Parameter("arg", "secondOne")).call();
        assertEquals("secondOne", sos.toString());
    }

    @Test
    public void threadSafety() throws Exception {
        ArrayBlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(100);
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(5, 5, 10, SECONDS, workQueue);

        for (int i = 0; i < 100; i++) {
            tpe.submit(createWork(i));
        }

        tpe.shutdown();
        tpe.awaitTermination(60, SECONDS);

    }

    private Runnable createWork(final int number) {
        return new Runnable() {
            public void run() {
                ByteArrayOutputStream sos = new ByteArrayOutputStream();

                newInstance(sos, "out.print arg", new Parameter("arg", "number " + number)).call();
                errorCollector.checkThat("number " + number, is(sos.toString()));
            }
        };
    }

    private GroovyScript newInstance(ByteArrayOutputStream sos, String scriptSource, Parameter... params) {
        GroovyScript gs = new GroovyScript(scriptSource, params, true, new StreamTaskListener(sos)) {
            @Override
            public ClassLoader getClassLoader() {
                return Thread.currentThread().getContextClassLoader();
            }
        };
        return gs;
    }
}
