package org.jenkinsci.plugins.scriptler.util;

import groovy.lang.GroovyShell;
import groovy.lang.Script;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.remoting.DelegatingCallable;

import java.io.PrintStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import jenkins.model.Jenkins;

import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.collections.map.LRUMap;
import org.jenkinsci.plugins.scriptler.Messages;
import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.jenkinsci.remoting.RoleChecker;

/**
 * Inspired by hudson.util.RemotingDiagnostics.Script, but adding parameters.
 */
public class GroovyScript extends MasterToSlaveCallable<Object, RuntimeException> {
    private static final long serialVersionUID = 1L;
    private final String script;
    private final Parameter[] parameters;
    private final boolean failWithException;
    private final TaskListener listener;
    private transient final AbstractBuild<?, ?> build;
    private transient final Launcher launcher;
    private transient ClassLoader cl;

    @SuppressWarnings("unchecked")
    private static Map<String, ConcurrentLinkedQueue<Script>> cache = Collections.synchronizedMap(new LRUMap(10));

    private static final Set<String> DEFAULT_VARIABLES = new HashSet<String>();
    static {
        DEFAULT_VARIABLES.add("out");
        DEFAULT_VARIABLES.add("build");
        DEFAULT_VARIABLES.add("listener");
        DEFAULT_VARIABLES.add("launcher");
    }

    /**
     * This constructor can only be used when the script is executed on the master, because launcher and build can not be transered to a slave and the therefore the execution will fail
     * @param script the script to be executed
     * @param parameters the parameters to be passed to the script
     * @param failWithException should the job fail with an exception
     * @param listener access to logging via listener
     * @param launcher the launcher
     * @param build the current build
     */
    public GroovyScript(String script, Parameter[] parameters, boolean failWithException, TaskListener listener, Launcher launcher, AbstractBuild<?, ?> build) {
        this.script = script;
        this.parameters = parameters;
        this.failWithException = failWithException;
        this.listener = listener;
        this.cl = getClassLoader();
        this.build = build;
        this.launcher = launcher;
    }
    
    /**
     * Constructor 
     * @param script the script to be executed
     * @param parameters the parameters to be passed to the script
     * @param failWithException should the job fail with an exception
     * @param listener access to logging via listener
     */
    public GroovyScript(String script, Parameter[] parameters, boolean failWithException, TaskListener listener) {
        this(script, parameters, failWithException, listener, null, null);
    }

    public ClassLoader getClassLoader() {
        return Jenkins.getInstance().getPluginManager().uberClassLoader;
    }

    public Object call() throws RuntimeException {
        // if we run locally, cl!=null. Otherwise the delegating classloader will be available as context classloader.
        if (cl == null) {
            cl = Thread.currentThread().getContextClassLoader();
        }
        PrintStream logger = listener.getLogger();
        GroovyShell shell = new GroovyShell(cl);

        if(parameters != null) {
            for (Parameter param : parameters) {
                final String paramName = param.getName();
                if (DEFAULT_VARIABLES.contains(paramName)) {
                    logger.println(Messages.skipParamter(paramName));
                } else {
                    shell.setVariable(paramName, param.getValue());
                }
            }
        }

        // set default variables
        shell.setVariable("out", logger);
        shell.setVariable("listener", listener);
        if(build != null) shell.setVariable("build", build);
        if(launcher != null) shell.setVariable("launcher", launcher);

        ConcurrentLinkedQueue<Script> scriptPool = cache.get(script);
        if (scriptPool == null) {
            scriptPool = new ConcurrentLinkedQueue<Script>();
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

    private static final class ScriptlerExecutionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ScriptlerExecutionException(Throwable cause) {
            super(cause);
        }
    }
}