package org.jenkinsci.plugins.scriptler.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.util.StreamTaskListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.umd.cs.findbugs.annotations.CheckForNull;;
import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.scriptler.Messages;
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
public class ScriptHelper {

    private final static Logger LOGGER = Logger.getLogger(ScriptHelper.class.getName());

    private static final Pattern SCRIPT_META_PATTERN = Pattern.compile(".*BEGIN META(.+?)END META.*", Pattern.DOTALL);
    private static final Map<String, Class<?>> JSON_CLASS_MAPPING = new HashMap<>();
    static {
        JSON_CLASS_MAPPING.put("authors", Author.class);
        JSON_CLASS_MAPPING.put("parameters", Parameter.class);
    }

    @NonNull
    public static String readScriptFromFile(@NonNull File file) throws IOException {
        return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    }

    public static void writeScriptToFile(@NonNull File file, @NonNull String script) throws IOException {
        FileUtils.writeStringToFile(file, script, StandardCharsets.UTF_8);
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
    public static @CheckForNull Script getScript(String id, boolean withSrc) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        Script s = ScriptlerConfiguration.getConfiguration().getScriptById(id);
        if (withSrc && s != null) {
            File scriptSrc = new File(ScriptlerManagement.getScriptDirectory(), s.getScriptPath());
            try {
                s.setScript(readScriptFromFile(scriptSrc));
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, Messages.scriptSourceNotFound(id));
            }
        }
        return s;
    }
    
    /**
     * @since TODO
     */
    public static void putScriptInApprovalQueueIfRequired(String scriptSourceCode){
        // we cannot use sandbox since the script is potentially sent to slaves
        // and the sandbox mode is not meant to be used with remoting
        ScriptApproval.get().configuring(scriptSourceCode, GroovyLanguage.get(), ApprovalContext.create().withCurrentUser());
    }
    
    /**
     * @param scriptSourceCode Source code that must be approved
     * @return true if the script was approved or created by a user with RUN_SCRIPT permission
     * @since TODO
     */
    public static boolean isApproved(String scriptSourceCode){
        return isApproved(scriptSourceCode, true);
    }
    
    /**
     * @param scriptSourceCode Source code that must be approved
     * @param putInApprovalQueueIfNotApprovedYet true means we try to know if the user has permission
     *                                          to approve the script automatically in case it was not approved yet
     * @return true if the script is approved
     * @since TODO
     */
    public static boolean isApproved(String scriptSourceCode, boolean putInApprovalQueueIfNotApprovedYet){
        try{
            ScriptApproval.get().using(scriptSourceCode, GroovyLanguage.get());
            return true;
        }
        catch(UnapprovedUsageException e){
            if(putInApprovalQueueIfNotApprovedYet){
                // in case there is some ways that are not covered
                putScriptInApprovalQueueIfRequired(scriptSourceCode);
                try{
                    ScriptApproval.get().using(scriptSourceCode, GroovyLanguage.get());
                    // user has permission to approve the script
                    return true;
                }
                catch(UnapprovedUsageException e2){
                    // user does not have the permission to approve the script
                }
            }
            return false;
        }
    }

    public static String runScript(String[] slaves, String scriptTxt, @NonNull Collection<Parameter> parameters) throws IOException, ServletException {
        StringBuffer output = new StringBuffer();
        for (String slave : slaves) {
            LOGGER.log(Level.FINE, "here is the node -> " + slave);
            output.append("___________________________________________\n");
            output.append("[" + slave + "]:\n");
            output.append(ScriptHelper.runScript(slave, scriptTxt, parameters));
        }
        output.append("___________________________________________\n");
        return output.toString();
    }

    /**
     * Runs the execution on a given slave.
     * 
     * @param node
     *            where to run the script.
     * @param scriptTxt
     *            the script (groovy) to be executed.
     * @return the output
     * @throws IOException
     * @throws ServletException
     */
    public static String runScript(String node, String scriptTxt, @NonNull Collection<Parameter> parameters) throws IOException, ServletException {

        Object output = "[no output]";
        ByteArrayOutputStream sos = new ByteArrayOutputStream();
        if (node != null && scriptTxt != null) {

            try {
                Computer comp = Jenkins.get().getComputer(node);
                if (comp == null && "(master)".equals(node)) {
                    output = FilePath.localChannel.call(new GroovyScript(scriptTxt, parameters, false, new StreamTaskListener(sos, StandardCharsets.UTF_8)));
                } else if (comp == null) {
                    output = Messages.node_not_found(node) + "\n";
                } else {
                    if (comp.getChannel() == null) {
                        output = Messages.node_not_online(node) + "\n";
                    }

                    else {
                        output = comp.getChannel().call(new GroovyScript(scriptTxt, parameters, false, new StreamTaskListener(sos, StandardCharsets.UTF_8)));
                    }
                }

            } catch (InterruptedException e) {
                throw new ServletException(e);
            }
        }
        return new String(sos.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Returns the meta info of a script body, the meta info has to follow the convention at https://github.com/jenkinsci/jenkins-scripts/tree/master/scriptler
     * 
     * @param fullScriptBody
     *            the script to extract the meta info from
     * @return <code>null</code> if no meta info found
     * @see <a href="https://github.com/jenkinsci/jenkins-scripts/tree/master/scriptler">...</a>
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
