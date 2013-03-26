/**
 * 
 */
package org.jenkinsci.plugins.scriptler.builder;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.ParameterValue;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ParametersAction;
import hudson.model.Project;
import hudson.security.Permission;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import jenkins.model.Jenkins.MasterComputer;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.scriptler.Messages;
import org.jenkinsci.plugins.scriptler.ScriptlerManagment;
import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.jenkinsci.plugins.scriptler.config.Script;
import org.jenkinsci.plugins.scriptler.config.ScriptlerConfiguration;
import org.jenkinsci.plugins.scriptler.util.GroovyScript;
import org.jenkinsci.plugins.scriptler.util.ScriptHelper;
import org.jenkinsci.plugins.scriptler.util.UIHelper;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.bind.JavaScriptMethod;

/**
 * @author Dominik Bartholdi (imod)
 * 
 */
public class ScriptlerBuilder extends Builder implements Serializable {
    private static final long serialVersionUID = 1L;

    private final static Logger LOGGER = Logger.getLogger(ScriptlerBuilder.class.getName());

    // this is only used to identify the builder if a user without privileges modifies the job.
    private String builderId;
    private String scriptId;
    private boolean propagateParams = false;
    private Parameter[] parameters;

    public ScriptlerBuilder(String builderId, String scriptId, boolean propagateParams, Parameter[] parameters) {
        this.builderId = builderId;
        this.scriptId = scriptId;
        this.parameters = parameters;
        this.propagateParams = propagateParams;
    }

    public String getScriptId() {
        return scriptId;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public String getBuilderId() {
        return builderId;
    }

    public boolean isPropagateParams() {
        return propagateParams;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        boolean isOk = false;
        final Script script = ScriptHelper.getScript(scriptId, true);

        if (script != null) {
            try {

                // expand the parameters before passing these to the execution, this is to allow any token macro to resolve parameter values
                List<Parameter> expandedParams = new LinkedList<Parameter>();

                if (propagateParams) {
                    final ParametersAction paramsAction = build.getAction(ParametersAction.class);
                    if (paramsAction == null) {
                        listener.getLogger().println(Messages.no_parameters_defined());
                    } else {
                        final List<ParameterValue> jobParams = paramsAction.getParameters();
                        for (ParameterValue parameterValue : jobParams) {
                            // pass the params to the token expander in a way that these get expanded by environment variables (params are also environment variables)
                            expandedParams.add(new Parameter(parameterValue.getName(), TokenMacro.expandAll(build, listener, "${" + parameterValue.getName() + "}")));
                        }
                    }
                }
                for (Parameter parameter : parameters) {
                    expandedParams.add(new Parameter(parameter.getName(), TokenMacro.expandAll(build, listener, parameter.getValue().toString())));
                }
                final Object output;
                if (script.onlyMaster) {
                    // When run on master, make build, launcher, listener available to script
                    expandedParams.add(new Parameter("build", build));
                    expandedParams.add(new Parameter("launcher", launcher));
                    expandedParams.add(new Parameter("listener", listener));
                	
                    output = MasterComputer.localChannel.call(new GroovyScript(script.script, expandedParams.toArray(new Parameter[expandedParams.size()]), true, listener));
                } else {
                    output = launcher.getChannel().call(new GroovyScript(script.script, expandedParams.toArray(new Parameter[expandedParams.size()]), true, listener));
                }
                if (output instanceof Boolean && Boolean.FALSE.equals(output)) {
                    isOk = false;
                } else {
                    isOk = true;
                }
            } catch (Exception e) {
                listener.getLogger().print(Messages.scriptExecutionFailed(scriptId) + " - " + e.getMessage());
                e.printStackTrace(listener.getLogger());
            }
        } else {
            if (StringUtils.isBlank(scriptId)) {
                listener.getLogger().print(Messages.scriptNotDefined());
            } else {
                listener.getLogger().print(Messages.scriptNotFound(scriptId));
            }
        }
        return isOk;
    }

    // Overridden for better type safety.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private static final AtomicInteger CURRENT_ID = new AtomicInteger();

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return Jenkins.getInstance().hasPermission(Jenkins.RUN_SCRIPTS);
        }

        @Override
        public String getDisplayName() {
            return Messages.builder_name();
        }

        public Permission getRequiredPermission() {
            return getScriptler().getRequiredPermissionForRunScript();
        }

        @Override
        public ScriptlerBuilder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            ScriptlerBuilder builder = null;
            String builderId = formData.optString("builderId");

            if (!Jenkins.getInstance().hasPermission(Jenkins.RUN_SCRIPTS)) {
                // the user has no permission to change the builders, therefore we reload the builder without his changes!
                final String backupJobName = formData.optString("backupJobName");

                if (StringUtils.isNotBlank(builderId) && StringUtils.isNotBlank(backupJobName)) {
                    final Project<?, ?> project = Jenkins.getInstance().getItemByFullName(backupJobName, Project.class);
                    final List<Builder> builders = project.getBuilders();
                    for (Builder b : builders) {
                        if (b instanceof ScriptlerBuilder) {
                            ScriptlerBuilder sb = (ScriptlerBuilder) b;
                            if (builderId.equals(sb.getBuilderId())) {
                                LOGGER.log(Level.FINE, "reloading ScriptlerBuilder [" + builderId + "] on project [" + backupJobName + "], as user has no permission to change it!");
                                return sb;
                            }
                        }
                    }
                }

            } else {
                final String id = formData.optString("scriptlerScriptId");
                final boolean inPropagateParams = formData.getBoolean("propagateParams");
                if (StringUtils.isBlank(builderId)) {
                    // create a unique id - this is only used to identify the builder if a user without privileges modifies the job.
                    builderId = System.currentTimeMillis() + "_" + CURRENT_ID.addAndGet(1);
                }
                if (StringUtils.isNotBlank(id)) {
                    Parameter[] params = null;
                    try {
                        params = UIHelper.extractParameters(formData);
                    } catch (ServletException e) {
                        throw new FormException(Messages.parameterExtractionFailed(), "parameters");
                    }
                    builder = new ScriptlerBuilder(builderId, id, inPropagateParams, params);
                }
            }
            if (builder == null) {
                builder = new ScriptlerBuilder(builderId, null, false, null);
            }
            return builder;
        }

        public Set<Script> getScripts() {
            // TODO currently only script for RUN_SCRIPT permissions are returned?
            final Set<Script> scripts = getConfig().getScripts();
            final Set<Script> scriptsForBuilder = new HashSet<Script>();
            for (Script script : scripts) {
                if (script.nonAdministerUsing) {
                    scriptsForBuilder.add(script);
                }
            }
            return scriptsForBuilder;
        }

        private ScriptlerManagment getScriptler() {
            return Jenkins.getInstance().getExtensionList(ScriptlerManagment.class).get(0);
        }

        private ScriptlerConfiguration getConfig() {
            return getScriptler().getConfiguration();
        }

        /**
         * gets the argument description to be displayed on the screen when selecting a config in the dropdown
         * 
         * @param configId
         *            the config id to get the arguments description for
         * @return the description
         */
        @JavaScriptMethod
        public JSONArray getParameters(String scriptlerScriptId) {
            final Script script = getConfig().getScriptById(scriptlerScriptId);
            if (script != null && script.getParameters() != null) {
                return JSONArray.fromObject(script.getParameters());
            }
            return null;
        }

    }
}
