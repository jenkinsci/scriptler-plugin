package org.jenkinsci.plugins.scriptler.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import hudson.model.TaskListener;
import java.io.PrintStream;
import java.io.Serial;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.collections.map.LRUMap;
import org.jenkinsci.plugins.scriptler.Messages;
import org.jenkinsci.plugins.scriptler.config.Parameter;

/**
 * Inspired by hudson.util.RemotingDiagnostics.Script, but adding parameters.
 */
public class GroovyScript extends MasterToSlaveCallable<Object, RuntimeException> {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String script;

    @NonNull
    private final Collection<Parameter> parameters;

    private final boolean failWithException;
    private final TaskListener listener;

    @SuppressWarnings("unchecked")
    private static final Map<String, ConcurrentLinkedQueue<Script>> cache = Collections.synchronizedMap(new LRUMap(10));

    private static final Set<String> DEFAULT_VARIABLES = new HashSet<>();

    static {
        DEFAULT_VARIABLES.add("out");
        DEFAULT_VARIABLES.add("build");
        DEFAULT_VARIABLES.add("listener");
        DEFAULT_VARIABLES.add("launcher");
    }

    /**
     * Constructor
     * @param script the script to be executed
     * @param parameters the parameters to be passed to the script
     * @param failWithException should the job fail with an exception
     * @param listener access to logging via listener
     */
    public GroovyScript(
            String script,
            @NonNull Collection<Parameter> parameters,
            boolean failWithException,
            TaskListener listener) {
        this.script = script;
        this.parameters = new ArrayList<>(parameters);
        this.failWithException = failWithException;
        this.listener = listener;
    }

    public ClassLoader getClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    public Object call() {
        PrintStream logger = listener.getLogger();
        GroovyShell shell = new GroovyShell(getClassLoader());

        for (Parameter param : parameters) {
            final String paramName = param.getName();
            if (DEFAULT_VARIABLES.contains(paramName)) {
                logger.println(Messages.skipParameter(paramName));
            } else {
                shell.setVariable(paramName, param.getValue());
            }
        }

        // set default variables
        shell.setVariable("out", logger);
        setShellVariables(shell);

        ConcurrentLinkedQueue<Script> scriptPool = cache.get(script);
        if (scriptPool == null) {
            scriptPool = new ConcurrentLinkedQueue<>();
            cache.put(script, scriptPool);
            scriptPool = cache.get(script);
        }

        Script parsedScript = scriptPool.poll();

        try {
            if (parsedScript == null) {
                parsedScript = shell.parse(script);
            }

            parsedScript.setBinding(shell.getContext());

            Object output = parsedScript.run();
            if (output != null) {
                logger.println(Messages.resultPrefix() + " " + output);
                return output;
            } else {
                return "";
            }
        } catch (Throwable t) {
            if (failWithException) {
                throw new ScriptlerExecutionException(t);
            }
            t.printStackTrace(logger);
            return Boolean.FALSE;
        } finally {
            if (parsedScript != null) {
                scriptPool.add(parsedScript);
            }
        }
    }

    protected void setShellVariables(@NonNull GroovyShell shell) {
        shell.setVariable("listener", listener);
    }

    private static final class ScriptlerExecutionException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public ScriptlerExecutionException(Throwable cause) {
            super(cause);
        }
    }
}
