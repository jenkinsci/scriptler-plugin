package org.jenkinsci.plugins.scriptler.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.lang.GroovyShell;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import java.io.Serial;
import java.util.Collection;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.scriptler.config.Parameter;

public class ControllerGroovyScript extends GroovyScript {
    @Serial
    private static final long serialVersionUID = 1L;

    private final transient AbstractBuild<?, ?> build;
    private final transient Launcher launcher;

    /**
     * This constructor can only be used when the script is executed on the built-in node, because launcher and build
     * can not be transferred to an agent and therefore the execution will fail
     * @param script the script to be executed
     * @param parameters the parameters to be passed to the script
     * @param failWithException should the job fail with an exception
     * @param listener access to logging via listener
     * @param launcher the launcher
     * @param build the current build
     */
    public ControllerGroovyScript(
            String script,
            @NonNull Collection<Parameter> parameters,
            boolean failWithException,
            TaskListener listener,
            Launcher launcher,
            AbstractBuild<?, ?> build) {
        super(script, parameters, failWithException, listener);
        this.build = build;
        this.launcher = launcher;
    }

    @Override
    public ClassLoader getClassLoader() {
        return Jenkins.get().getPluginManager().uberClassLoader;
    }

    @Override
    protected void setShellVariables(@NonNull GroovyShell shell) {
        super.setShellVariables(shell);
        if (build != null) {
            shell.setVariable("build", build);
        }
        if (launcher != null) {
            shell.setVariable("launcher", launcher);
        }
    }
}
