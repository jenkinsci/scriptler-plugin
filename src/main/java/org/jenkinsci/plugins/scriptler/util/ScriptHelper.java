package org.jenkinsci.plugins.scriptler.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import jakarta.servlet.ServletException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.jenkinsci.plugins.scriptler.Messages;
import org.jenkinsci.plugins.scriptler.NodeNames;
import org.jenkinsci.plugins.scriptler.ScriptlerManagement;
import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.jenkinsci.plugins.scriptler.config.Script;
import org.jenkinsci.plugins.scriptler.config.ScriptlerConfiguration;
import org.jenkinsci.plugins.scriptler.share.ScriptInfo;
import org.jenkinsci.plugins.scriptler.share.ScriptInfo.Author;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.UnapprovedUsageException;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.GroovyLanguage;

/**
 *
 * @author Dominik Bartholdi (imod)
 *
 */
public final class ScriptHelper {

    private static final Logger LOGGER = Logger.getLogger(ScriptHelper.class.getName());

    private static final Pattern SCRIPT_META_PATTERN = Pattern.compile(".*BEGIN META(.+?)END META.*", Pattern.DOTALL);
    private static final Map<String, Class<?>> JSON_CLASS_MAPPING =
            Map.of("authors", Author.class, "parameters", Parameter.class);

    private ScriptHelper() {}

    @NonNull
    public static String readScriptFromFile(@NonNull Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public static void writeScriptToFile(@NonNull Path path, @NonNull String script) throws IOException {
        Files.writeString(path, script, StandardCharsets.UTF_8);
    }

    /**
     * Loads the script information.
     *
     * @param id
     *            the id of the script
     * @param withSrc
     *            should the script sources be loaded too?
     * @return the script - <code>null</code> if the id is not set or the script with the given id can not be resolved
     */
    public static @CheckForNull Script getScript(@CheckForNull String id, boolean withSrc) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Script s = ScriptlerConfiguration.getConfiguration().getScriptById(id);
        if (withSrc && s != null) {
            Path scriptSrc = ScriptlerManagement.getScriptDirectory2().resolve(s.getScriptPath());
            try {
                s.setScriptText(readScriptFromFile(scriptSrc));
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, Messages.scriptSourceNotFound(id));
            }
        }
        return s;
    }

    public static void putScriptInApprovalQueueIfRequired(@NonNull String scriptSourceCode) {
        // we cannot use sandbox since the script is potentially sent to agents
        // and the sandbox mode is not meant to be used with remoting
        ScriptApproval.get()
                .configuring(
                        scriptSourceCode,
                        GroovyLanguage.get(),
                        ApprovalContext.create().withCurrentUser(),
                        true);
    }

    /**
     * @param scriptSourceCode Source code that must be approved
     * @return true if the script was approved or created by a user with RUN_SCRIPT permission
     */
    public static boolean isApproved(@CheckForNull String scriptSourceCode) {
        return isApproved(scriptSourceCode, true);
    }

    /**
     * @param scriptSourceCode Source code that must be approved
     * @param putInApprovalQueueIfNotApprovedYet true means we try to know if the user has permission
     *                                          to approve the script automatically in case it was not approved yet
     * @return true if the script is approved
     */
    public static boolean isApproved(
            @CheckForNull String scriptSourceCode, boolean putInApprovalQueueIfNotApprovedYet) {
        if (scriptSourceCode == null) {
            return false;
        }
        try {
            ScriptApproval.get().using(scriptSourceCode, GroovyLanguage.get());
            return true;
        } catch (UnapprovedUsageException e) {
            if (putInApprovalQueueIfNotApprovedYet) {
                // in case there is some ways that are not covered
                putScriptInApprovalQueueIfRequired(scriptSourceCode);
                try {
                    ScriptApproval.get().using(scriptSourceCode, GroovyLanguage.get());
                    // user has permission to approve the script
                    return true;
                } catch (UnapprovedUsageException e2) {
                    // user does not have the permission to approve the script
                }
            }
            return false;
        }
    }

    /**
     * @deprecated Use {@link #runScript(List, String, Collection)} instead.
     */
    @Deprecated(since = "381")
    public static String runScript(String[] computers, String scriptText, @NonNull Collection<Parameter> parameters)
            throws IOException, ServletException {
        return runScript(Arrays.asList(computers), scriptText, parameters);
    }

    public static String runScript(List<String> computers, String scriptText, @NonNull Collection<Parameter> parameters)
            throws IOException, ServletException {
        StringBuilder output = new StringBuilder();
        for (String computer : computers) {
            LOGGER.log(Level.FINE, "here is the node -> {0}", computer);
            output.append("___________________________________________\n");
            output.append("[").append(computer).append("]:\n");
            output.append(ScriptHelper.runScript(computer, scriptText, parameters));
        }
        output.append("___________________________________________\n");
        return output.toString();
    }

    /**
     * Runs the execution on a given agent.
     *
     * @param node
     *            where to run the script.
     * @param scriptTxt
     *            the script (groovy) to be executed.
     * @return the output
     */
    public static String runScript(String node, String scriptTxt, @NonNull Collection<Parameter> parameters)
            throws IOException, ServletException {

        ByteArrayOutputStream sos = new ByteArrayOutputStream();
        if (node != null && scriptTxt != null) {

            try {
                Computer comp = Jenkins.get().getComputer(node);
                TaskListener listener = new StreamTaskListener(sos, StandardCharsets.UTF_8);
                if (NodeNames.BUILT_IN.equals(node)) {
                    FilePath.localChannel.call(new ControllerGroovyScript(
                            scriptTxt,
                            parameters,
                            false,
                            listener,
                            Jenkins.get().createLauncher(listener),
                            null));
                } else if (comp != null && comp.getChannel() != null) {
                    comp.getChannel().call(new GroovyScript(scriptTxt, parameters, false, listener));
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServletException(e);
            }
        }
        return sos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Returns the meta info of a script body, which must follow <a href="https://github.com/jenkinsci/jenkins-scripts/tree/main/scriptler">the convention</a>
     *
     * @param fullScriptBody
     *            the script to extract the meta info from
     * @return <code>null</code> if no meta info found
     * @see <a href="https://github.com/jenkinsci/jenkins-scripts/tree/main/scriptler">...</a>
     */
    public static ScriptInfo extractScriptInfo(String fullScriptBody) {
        final Matcher matcher = SCRIPT_META_PATTERN.matcher(fullScriptBody);
        if (matcher.find()) {
            final String group = matcher.group(1);
            final JSONObject json = (JSONObject) JSONSerializer.toJSON(group.trim());
            return (ScriptInfo) JSONObject.toBean(json, ScriptInfo.class, JSON_CLASS_MAPPING);
        }
        return null;
    }
}
