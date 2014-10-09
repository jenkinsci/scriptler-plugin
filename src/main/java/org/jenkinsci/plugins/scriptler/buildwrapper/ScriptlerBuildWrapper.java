/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jenkinsci.plugins.scriptler.buildwrapper;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Project;
import hudson.security.Permission;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
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
 *
 * @author stuartr
 */
public class ScriptlerBuildWrapper extends BuildWrapper {
    
    private final static Logger LOGGER = Logger.getLogger(ScriptlerBuildWrapper.class.getName());
    
    private String buildWrapperId;
    private String scriptId;
    private boolean propagateParams = true;
    private Parameter[] parameters;
    
    public ScriptlerBuildWrapper(String buildWrapperId, String scriptId, boolean propagateParams, Parameter[] parameters) {
        this.buildWrapperId = buildWrapperId;
        this.scriptId = scriptId;
        this.propagateParams = propagateParams;
        this.parameters = parameters;
    }

    public String getScriptId() {
        return scriptId;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
    public String getBuildWrapperId() {
        return buildWrapperId;
    }

    public boolean isPropagateParams() {
        return propagateParams;
    }
    
    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        
        Map<String,String> injectedEnvVars = null;        
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
                            expandedParams.add(new Parameter(parameterValue.getName(), TokenMacro.expandAll(build, listener, "${" + parameterValue.getName() + "}", false, null)));
                        }
                    }
                }
                for (Parameter parameter : parameters) {
                    expandedParams.add(new Parameter(parameter.getName(), TokenMacro.expandAll(build, listener, parameter.getValue())));
                }
                final Object output;
                if (script.onlyMaster) {
                    // When run on master, make build, launcher, listener available to script
                    output = Jenkins.MasterComputer.localChannel.call(new GroovyScript(script.script, expandedParams.toArray(new Parameter[expandedParams.size()]), true, listener, launcher, build));
                } else {
                    output = launcher.getChannel().call(new GroovyScript(script.script, expandedParams.toArray(new Parameter[expandedParams.size()]), true, listener));
                }
                if (output instanceof Map) {
                    injectedEnvVars = (Map<String,String>) output;
                    isOk = true;
                } else {
                    isOk = false;
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
        
        if(isOk) {
            return new ScriptlerBuildWrapperEnvironment(injectedEnvVars);
        }
        
        return null;
    }
    
    public class ScriptlerBuildWrapperEnvironment extends Environment {

        private Map<String,String> injectedEnvVars;
        
        public ScriptlerBuildWrapperEnvironment(Map<String,String> envVars) {
            this.injectedEnvVars = envVars;
        }

        @Override
        public void buildEnvVars(Map<String, String> env) {
            // inject parameters into build environment
            env.putAll(this.injectedEnvVars);
        }
    }


    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        private static final AtomicInteger CURRENT_ID = new AtomicInteger();
        
        public boolean isApplicable(AbstractProject<?, ?> item) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return Messages.buildWrapper_name();
        }
        
        public Permission getRequiredPermission() {
            return getScriptler().getRequiredPermissionForRunScript();
        }
        
        @Override
        public ScriptlerBuildWrapper newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            ScriptlerBuildWrapper buildWrapper = null;
            String buildWrapperId = formData.optString("buildWrapperId");
            
            if (!Jenkins.getInstance().hasPermission(Jenkins.RUN_SCRIPTS)) {
                // the user has no permission to change the builders, therefore we reload the builder without his changes!
                final String backupJobName = formData.optString("backupJobName");

                if (StringUtils.isNotBlank(buildWrapperId) && StringUtils.isNotBlank(backupJobName)) {
                    final Project<?, ?> project = Jenkins.getInstance().getItemByFullName(backupJobName, Project.class);
                    final Map<Descriptor<BuildWrapper>,BuildWrapper> buildWrappers = project.getBuildWrappers();
                    for (BuildWrapper b : buildWrappers.values()) {
                        if (b instanceof ScriptlerBuildWrapper) {
                            ScriptlerBuildWrapper sb = (ScriptlerBuildWrapper) b;
                            if (buildWrapperId.equals(sb.getBuildWrapperId())) {
                                LOGGER.log(Level.FINE, "reloading ScriptlerBuilder [" + buildWrapperId + "] on project [" + backupJobName + "], as user has no permission to change it!");
                                return sb;
                            }
                        }
                    }
                }

            } else {
                final String id = formData.optString("scriptlerScriptId");
                final boolean inPropagateParams = formData.getBoolean("propagateParams");
                if (StringUtils.isBlank(buildWrapperId)) {
                    // create a unique id - this is only used to identify the builder if a user without privileges modifies the job.
                    buildWrapperId = System.currentTimeMillis() + "_" + CURRENT_ID.addAndGet(1);
                }
                if (StringUtils.isNotBlank(id)) {
                    Parameter[] params = null;
                    try {
                        params = UIHelper.extractParameters(formData);
                    } catch (ServletException e) {
                        throw new FormException(Messages.parameterExtractionFailed(), "parameters");
                    }
                    buildWrapper = new ScriptlerBuildWrapper(buildWrapperId, id, inPropagateParams, params);
                }
            }
            if (buildWrapper == null) {
                buildWrapper = new ScriptlerBuildWrapper(buildWrapperId, null, false, null);
            }
            return buildWrapper;
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
