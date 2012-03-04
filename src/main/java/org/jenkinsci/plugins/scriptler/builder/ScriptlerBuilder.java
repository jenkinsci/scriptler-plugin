/**
 * 
 */
package org.jenkinsci.plugins.scriptler.builder;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.security.Permission;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

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
 * @author Dominik Bartholdi (imod)
 * 
 */
public class ScriptlerBuilder extends Builder implements Serializable {
    private static final long serialVersionUID = 1L;
    private String scriptId;
    private Parameter[] parameters;

    public ScriptlerBuilder(String scriptId, Parameter[] parameters) {
        this.scriptId = scriptId;
        this.parameters = parameters;
    }

    public String getScriptId() {
        return scriptId;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        boolean isOk = false;
        final Script script = ScriptHelper.getScript(scriptId, true);
        if (script != null) {
            try {
                // expand the parameters before passing these to the execution, this is to allow any token macro to resolve parameter values
                Parameter[] expandedParams = new Parameter[parameters.length];
                for (int i = 0; i < parameters.length; i++) {
                    Parameter parameter = parameters[i];
                    expandedParams[i] = new Parameter(parameter.getName(), TokenMacro.expandAll(build, listener, parameter.getValue()));
                }
                final String output = launcher.getChannel().call(new GroovyScript(script.script, expandedParams, true));
                listener.getLogger().print(output);
                isOk = true;
            } catch (Exception e) {
                listener.getLogger().print(Messages.scriptExecutionFailed(scriptId) + " - " + e.getMessage());
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
            final String id = formData.optString("scriptlerScriptId");
            ScriptlerBuilder builder = null;
            if (StringUtils.isNotBlank(id)) {
                Parameter[] params = null;
                try {
                    params = UIHelper.extractParameters(formData);
                } catch (ServletException e) {
                    throw new FormException(Messages.parameterExtractionFailed(), "parameters");
                }
                builder = new ScriptlerBuilder(id, params);
            } else {
                builder = new ScriptlerBuilder(null, null);
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
