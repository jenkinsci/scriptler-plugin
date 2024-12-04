/**
 *
 */
package org.jenkinsci.plugins.scriptler.builder;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Failure;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Project;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormApply;
import hudson.util.QuotedStringTokenizer;
import hudson.util.XStream2;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jenkins.model.Jenkins;
import jenkins.util.xstream.CriticalXStreamException;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.scriptler.Messages;
import org.jenkinsci.plugins.scriptler.ScriptlerManagement;
import org.jenkinsci.plugins.scriptler.ScriptlerPermissions;
import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.jenkinsci.plugins.scriptler.config.Script;
import org.jenkinsci.plugins.scriptler.config.ScriptlerConfiguration;
import org.jenkinsci.plugins.scriptler.util.ControllerGroovyScript;
import org.jenkinsci.plugins.scriptler.util.GroovyScript;
import org.jenkinsci.plugins.scriptler.util.ScriptHelper;
import org.jenkinsci.plugins.scriptler.util.UIHelper;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.bind.JavaScriptMethod;

/**
 * @author Dominik Bartholdi (imod)
 *
 */
public class ScriptlerBuilder extends Builder implements Serializable {
    private static final AtomicInteger CURRENT_ID = new AtomicInteger();

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(ScriptlerBuilder.class.getName());
    private static final String BUILDER_ID = "builderId";

    // this is only used to identify the builder if a user without privileges modifies the job.
    @CheckForNull
    private final String builderId;

    @CheckForNull
    private final String scriptId;

    private final boolean propagateParams;

    @NonNull
    private final List<Parameter> parameters;

    /**
     * @deprecated as of 3.5; use {@link #ScriptlerBuilder(String, String, boolean, List)}
     */
    @Deprecated(since = "3.5")
    public ScriptlerBuilder(
            @CheckForNull String builderId,
            @CheckForNull String scriptId,
            boolean propagateParams,
            @CheckForNull Parameter[] parameters) {
        this(builderId, scriptId, propagateParams, parameters == null ? List.of() : List.of(parameters));
    }

    @DataBoundConstructor
    public ScriptlerBuilder(
            @CheckForNull String builderId,
            @CheckForNull String scriptId,
            boolean propagateParams,
            @CheckForNull List<Parameter> parameters) {
        this.builderId = builderId;
        this.scriptId = scriptId;
        this.parameters = parameters == null ? List.of() : List.copyOf(parameters);
        this.propagateParams = propagateParams;
    }

    private @NonNull Map<String, String> checkGenericData() {
        Map<String, String> errors = new HashMap<>();

        Script script = ScriptHelper.getScript(scriptId, true);
        if (script != null && !script.nonAdministerUsing) {
            errors.put("scriptId", "The script is not allowed to be executed in a build, check its configuration!");
        }

        checkPermission(errors);

        return errors;
    }

    private void checkPermission(@NonNull Map<String, String> errors) {
        if (Jenkins.get().hasPermission(ScriptlerPermissions.CONFIGURE)) {
            // user has right to add / edit Scriptler steps
            return;
        }

        Project<?, ?> project = retrieveProjectUsingCurrentRequest();
        if (!getAllScriptlerBuildersFromProject(project).contains(this)) {
            final String message = builderId == null || builderId.isBlank()
                    ? "As the given builder does not have ID, it must be equals to one of the existing builder that does not have ID"
                    : "The builderId must correspond to an existing builder of that project since the user does not have the rights to add/edit Scriptler step";
            errors.put(BUILDER_ID, message);
        }

        // else: we are not in a request context
    }

    /**
     * Must not be called inside XML processing since the modified data are not stored
     */
    private ScriptlerBuilder recreateBuilderWithBuilderIdIfRequired() {
        if (builderId == null || builderId.isBlank()) {
            return new ScriptlerBuilder(generateBuilderId(), scriptId, propagateParams, parameters);
        }
        return this;
    }

    private @NonNull List<ScriptlerBuilder> getAllScriptlerBuildersFromProject(@CheckForNull Project<?, ?> project) {
        if (project == null) {
            return List.of();
        }
        return project.getBuildersList().getAll(ScriptlerBuilder.class);
    }

    private @CheckForNull Project<?, ?> retrieveProjectUsingCurrentRequest() {
        return Optional.ofNullable(Stapler.getCurrentRequest2())
                .map(req -> req.findAncestorObject(Project.class))
                .orElse(null);
    }

    @CheckForNull
    public String getScriptId() {
        return scriptId;
    }

    /**
     * @deprecated since 3.5
     */
    @Deprecated(since = "3.5")
    public Parameter[] getParameters() {
        return parameters.toArray(new Parameter[0]);
    }

    @NonNull
    public List<Parameter> getParametersList() {
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
        final Script script = ScriptHelper.getScript(scriptId, true);

        if (script == null) {
            if (scriptId == null || scriptId.isBlank()) {
                LOGGER.log(Level.WARNING, "The script id was blank for the build {0}:{1}", new Object[] {
                    build.getProject().getName(), build.getDisplayName()
                });
                listener.getLogger().println(Messages.scriptNotDefined());
            } else {
                LOGGER.log(
                        Level.WARNING,
                        "The source corresponding to the scriptId {0} was not found (missing file ?) for the build {1}:{2}",
                        new Object[] {scriptId, build.getProject().getName(), build.getDisplayName()});
                listener.getLogger().println(Messages.scriptNotFound(scriptId));
            }
            return false;
        }

        boolean isOk = false;
        if (!script.nonAdministerUsing) {
            listener.getLogger().println(Messages.scriptNotUsableInBuildStep(script.getName()));
            LOGGER.log(
                    Level.WARNING,
                    "The script [{0} ({1})] is not allowed to be executed in a build, check its configuration. It concerns the build [{2}:{3}]",
                    new Object[] {
                        script.getName(), script.getId(), build.getProject().getName(), build.getDisplayName()
                    });
            return false;
        }

        if (!ScriptHelper.isApproved(script.getScriptText())) {
            listener.getLogger().println(Messages.scriptNotApprovedYet(script.getName()));
            LOGGER.log(
                    Level.WARNING,
                    "The script [{0} ({1})] is not approved yet, consider asking your administrator to approve it. It concerns the build [{2}:{3}]",
                    new Object[] {
                        script.getName(), script.getId(), build.getProject().getName(), build.getDisplayName()
                    });
            return false;
        }

        try {

            // expand the parameters before passing these to the execution, this is to allow any token macro to resolve
            // parameter values
            List<Parameter> expandedParams = new ArrayList<>();

            if (propagateParams) {
                final ParametersAction paramsAction = build.getAction(ParametersAction.class);
                if (paramsAction == null) {
                    listener.getLogger().println(Messages.no_parameters_defined());
                } else {
                    final List<ParameterValue> jobParams = paramsAction.getParameters();
                    for (ParameterValue parameterValue : jobParams) {
                        // pass the params to the token expander in a way that these get expanded by environment
                        // variables (params are also environment variables)
                        String macro = "${" + parameterValue.getName() + "}";
                        String value = TokenMacro.expandAll(build, listener, macro, false, null);
                        expandedParams.add(new Parameter(parameterValue.getName(), value));
                    }
                }
            }
            for (Parameter parameter : parameters) {
                expandedParams.add(new Parameter(
                        parameter.getName(), TokenMacro.expandAll(build, listener, parameter.getValue())));
            }
            final Object output;
            if (script.onlyBuiltIn) {
                // When run on the built-in node, make build, launcher, listener available to script
                output = FilePath.localChannel.call(new ControllerGroovyScript(
                        script.getScriptText(), expandedParams, true, listener, launcher, build));
            } else {
                VirtualChannel channel = launcher.getChannel();
                if (channel == null) {
                    output = null;
                    listener.getLogger()
                            .println(Messages.scriptExecutionFailed(scriptId) + " - " + Messages.agent_no_channel());
                } else {
                    output = channel.call(new GroovyScript(script.getScriptText(), expandedParams, true, listener));
                }
            }
            isOk = !Boolean.FALSE.equals(output);
        } catch (InterruptedException e) {
            listener.getLogger().println(Messages.scriptExecutionFailed(scriptId) + " - " + e.getMessage());
            e.printStackTrace(listener.getLogger());
            Thread.currentThread().interrupt();
        } catch (IOException | MacroEvaluationException e) {
            listener.getLogger().println(Messages.scriptExecutionFailed(scriptId) + " - " + e.getMessage());
            e.printStackTrace(listener.getLogger());
        }

        return isOk;
    }

    private static String generateBuilderId() {
        return System.currentTimeMillis() + "_" + CURRENT_ID.addAndGet(1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScriptlerBuilder that = (ScriptlerBuilder) o;

        return Objects.equals(propagateParams, that.propagateParams)
                && Objects.equals(builderId, that.builderId)
                && Objects.equals(scriptId, that.scriptId)
                && Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propagateParams, builderId, scriptId, parameters);
    }

    // Overridden for better type safety.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Automatically registered by XStream2.AssociatedConverterImpl#findConverter(Class)
     * Process the class regularly but add a check after that
     */
    @SuppressWarnings("unused") // discovered dynamically
    public static final class ConverterImpl extends XStream2.PassthruConverter<ScriptlerBuilder> {
        public ConverterImpl(XStream2 xstream) {
            super(xstream);
        }

        @Override
        protected void callback(ScriptlerBuilder obj, UnmarshallingContext context) {
            Map<String, String> errors = obj.checkGenericData();

            if (!errors.isEmpty()) {
                ConversionException conversionException = new ConversionException("Validation failed");
                errors.forEach(conversionException::add);
                throw new CriticalXStreamException(conversionException);
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return Jenkins.get().hasPermission(ScriptlerPermissions.RUN_SCRIPTS);
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.builder_name();
        }

        // used by Jelly views
        public Permission getRequiredPermission() {
            return ScriptlerPermissions.RUN_SCRIPTS;
        }

        @Override
        public ScriptlerBuilder newInstance(StaplerRequest2 req, JSONObject formData) {
            ScriptlerBuilder builder = null;
            String builderId = formData.optString(BUILDER_ID);
            String id = formData.optString("scriptlerScriptId");

            if (id != null && !id.isBlank()) {
                boolean inPropagateParams = formData.getBoolean("propagateParams");
                List<Parameter> params = UIHelper.extractParameters(formData);
                builder = new ScriptlerBuilder(builderId, id, inPropagateParams, params);
            }

            if (builder != null) {
                Map<String, String> errors = builder.checkGenericData();
                if (!errors.isEmpty()) {
                    throw new MultipleErrorFormValidation(errors);
                }
            }

            if (builder == null) {
                builder = new ScriptlerBuilder(builderId, null, false, List.of());
            }

            return builder.recreateBuilderWithBuilderIdIfRequired();
        }

        public List<Script> getScripts() {
            // TODO currently only script for RUN_SCRIPT permissions are returned?
            List<Script> scriptsForBuilder = new ArrayList<>();
            for (Script script : getConfig().getScripts()) {
                if (script.nonAdministerUsing) {
                    scriptsForBuilder.add(script);
                }
            }
            scriptsForBuilder.sort(Script.COMPARATOR_BY_NAME);
            return scriptsForBuilder;
        }

        private ScriptlerManagement getScriptler() {
            return ExtensionList.lookupSingleton(ScriptlerManagement.class);
        }

        private ScriptlerConfiguration getConfig() {
            return getScriptler().getConfiguration();
        }

        /**
         * gets the argument description to be displayed on the screen when selecting a config in the dropdown
         *
         * @param scriptlerScriptId
         *            the config id to get the arguments description for
         * @return the description
         */
        @JavaScriptMethod
        public JSONArray getParameters(String scriptlerScriptId) {
            final Script script = getConfig().getScriptById(scriptlerScriptId);
            if (script != null) {
                return JSONArray.fromObject(script.getParameters());
            }
            return null;
        }
    }

    /**
     * Notify the user with multiple message about the validation that failed
     */
    private static class MultipleErrorFormValidation extends RuntimeException implements HttpResponse {
        private final Map<String, String> fieldToMessage;

        public MultipleErrorFormValidation(Map<String, String> fieldToMessage) {
            this.fieldToMessage = fieldToMessage;
        }

        private String getAggregatedMessage() {
            return buildMessages().collect(Collectors.joining(", "));
        }

        private Stream<String> buildMessages() {
            return fieldToMessage.entrySet().stream().map(error -> error.getKey() + ": " + error.getValue());
        }

        @Override
        public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node)
                throws IOException, ServletException {
            if (FormApply.isApply(req)) {
                String script =
                        buildMessages().map(QuotedStringTokenizer::quote).collect(Collectors.joining(""));
                FormApply.showNotification(script, FormApply.NotificationType.ERROR)
                        .generateResponse(req, rsp, node);
            } else {
                new Failure(getAggregatedMessage()).generateResponse(req, rsp, node);
            }
        }
    }
}
