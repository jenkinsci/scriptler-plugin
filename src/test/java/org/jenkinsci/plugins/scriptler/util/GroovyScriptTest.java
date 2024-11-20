package org.jenkinsci.plugins.scriptler.util;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;

class GroovyScriptTest {
    @Test
    void scriptReturnFalse() {
        ByteArrayOutputStream sos = new ByteArrayOutputStream();
        GroovyScript gs = newInstance(sos, "return false");
        Object result = gs.call();
        assertTrue(sos.toString().contains("Result:   false"));
        assertEquals(Boolean.FALSE, result);
    }

    @Test
    void scriptReturnTrue() {
        ByteArrayOutputStream sos = new ByteArrayOutputStream();
        GroovyScript gs = newInstance(sos, "return true");
        Object result = gs.call();
        assertTrue(sos.toString().contains("Result:   true"));
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void helloWorld() {
        ByteArrayOutputStream sos = new ByteArrayOutputStream();
        GroovyScript gs = newInstance(sos, "out.print(\"HelloWorld\")");
        Object result = gs.call();
        assertEquals("HelloWorld", sos.toString());
        assertEquals("", result);
    }

    @Test
    void repeatedInvocation() {
        ByteArrayOutputStream sos = new ByteArrayOutputStream();

        newInstance(sos, "out.print arg", new Parameter("arg", "firstOne")).call();
        assertEquals("firstOne", sos.toString());

        sos.reset();
        newInstance(sos, "out.print arg", new Parameter("arg", "secondOne")).call();
        assertEquals("secondOne", sos.toString());
    }

    @Test
    void threadSafety() throws InterruptedException {
        ArrayBlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(100);
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(5, 5, 10, SECONDS, workQueue);
        List<AssertionError> assertionErrors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 100; i++) {
            tpe.submit(createWork(assertionErrors, i));
        }

        tpe.shutdown();
        tpe.awaitTermination(60, SECONDS);

        if (!assertionErrors.isEmpty()) {
            // borrowed from JUnit 5's assertAll() method
            MultipleFailuresError error = new MultipleFailuresError(null, assertionErrors);
            assertionErrors.forEach(error::addSuppressed);
            throw error;
        }
    }

    private Runnable createWork(final List<AssertionError> assertionErrors, final int number) {
        return () -> {
            ByteArrayOutputStream sos = new ByteArrayOutputStream();

            newInstance(sos, "out.print arg", new Parameter("arg", "number " + number))
                    .call();
            try {
                assertEquals("number " + number, sos.toString(StandardCharsets.UTF_8));
            } catch (AssertionError e) {
                assertionErrors.add(e);
            }
        };
    }

    private GroovyScript newInstance(ByteArrayOutputStream sos, String scriptSource, Parameter... params) {
        return new GroovyScript(
                scriptSource, Arrays.asList(params), true, new StreamTaskListener(sos, StandardCharsets.UTF_8)) {
            @Override
            public ClassLoader getClassLoader() {
                return Thread.currentThread().getContextClassLoader();
            }
        };
    }
}
