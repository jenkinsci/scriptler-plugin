package org.jenkinsci.plugins.scriptler.util;

import groovy.lang.GroovyShell;
import hudson.remoting.DelegatingCallable;

import java.io.PrintWriter;
import java.io.StringWriter;

import jenkins.model.Jenkins;

import org.jenkinsci.plugins.scriptler.Messages;
import org.jenkinsci.plugins.scriptler.config.Parameter;

/**
 * Inspired by hudson.util.RemotingDiagnostics.Script, but adding parameters.
 */
public final class GroovyScript implements DelegatingCallable<String, RuntimeException> {
    private static final long serialVersionUID = 1L;
    private final String script;
    private final Parameter[] parameters;
    private final boolean failWithException;
    private transient ClassLoader cl;

    private static final String PW_PARAM_VARIABLE = "out";

    public GroovyScript(String script, Parameter[] parameters, boolean failWithException) {
        this.script = script;
        this.parameters = parameters;
        this.failWithException = failWithException;
        cl = getClassLoader();
    }

    public ClassLoader getClassLoader() {
        return Jenkins.getInstance().getPluginManager().uberClassLoader;
    }

    public String call() throws RuntimeException {
        // if we run locally, cl!=null. Otherwise the delegating classloader will be available as context classloader.
        if (cl == null) {
            cl = Thread.currentThread().getContextClassLoader();
        }
        GroovyShell shell = new GroovyShell(cl);

        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);

        for (Parameter param : parameters) {
            final String paramName = param.getName();
            if (PW_PARAM_VARIABLE.equals(paramName)) {
                pw.write(Messages.skipParamter(PW_PARAM_VARIABLE));
            } else {
                shell.setVariable(paramName, param.getValue());
            }
        }
        shell.setVariable(PW_PARAM_VARIABLE, pw);
        try {
            Object output = shell.evaluate(script);
            if (output != null) {
                pw.println(Messages.resultPrefix() + " " + output);
            }
        } catch (Throwable t) {
            if (failWithException) {
                throw new ScriptlerExecutionException(t);
            }
            t.printStackTrace(pw);
        }
        return out.toString();
    }

    private static final class ScriptlerExecutionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ScriptlerExecutionException(Throwable cause) {
            super(cause);
        }
    }
}