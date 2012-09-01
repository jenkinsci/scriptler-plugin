package org.jenkinsci.plugins.scriptler.util;

import groovy.lang.GroovyShell;
import hudson.model.TaskListener;
import hudson.remoting.DelegatingCallable;

import java.io.PrintWriter;

import jenkins.model.Jenkins;

import org.jenkinsci.plugins.scriptler.Messages;
import org.jenkinsci.plugins.scriptler.config.Parameter;

/**
 * Inspired by hudson.util.RemotingDiagnostics.Script, but adding parameters.
 */
public class GroovyScript implements DelegatingCallable<Object, RuntimeException>{
    private static final long serialVersionUID = 1L;
    private final String script;
    private final Parameter[] parameters;
    private final boolean failWithException;
    private final TaskListener listener;
    private transient ClassLoader cl;

    private static final String PW_PARAM_VARIABLE = "out";

    public GroovyScript(String script, Parameter[] parameters, boolean failWithException, TaskListener listener) {
        this.script = script;
        this.parameters = parameters;
        this.failWithException = failWithException;
        this.listener = listener;
        cl = getClassLoader();
    }

    public ClassLoader getClassLoader() {
        return Jenkins.getInstance().getPluginManager().uberClassLoader;
    }

    public Object call() throws RuntimeException {
        // if we run locally, cl!=null. Otherwise the delegating classloader will be available as context classloader.
        if (cl == null) {
            cl = Thread.currentThread().getContextClassLoader();
        }
        PrintWriter pw = new PrintWriter(listener.getLogger());
        GroovyShell shell = new GroovyShell(cl);

        for (Parameter param : parameters) {
            final String paramName = param.getName();
            if (PW_PARAM_VARIABLE.equals(paramName)) {
                pw.write(Messages.skipParamter(PW_PARAM_VARIABLE));
            } else {
                shell.setVariable(paramName, param.getValue());
            }
        }
        shell.setVariable(PW_PARAM_VARIABLE, listener.getLogger());
        try {
            Object output = shell.evaluate(script);
            if (output != null) {
                pw.println(Messages.resultPrefix() + " " + output);
                return output;
            } else {
                return "";
            }
        } catch (Throwable t) {
            if (failWithException) {
                throw new ScriptlerExecutionException(t);
            }
            t.printStackTrace(pw);
            return Boolean.FALSE;
        }
    }

    private static final class ScriptlerExecutionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ScriptlerExecutionException(Throwable cause) {
            super(cause);
        }
    }
}