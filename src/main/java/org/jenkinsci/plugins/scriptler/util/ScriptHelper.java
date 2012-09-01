package org.jenkinsci.plugins.scriptler.util;

import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.util.StreamTaskListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import jenkins.model.Jenkins.MasterComputer;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.scriptler.Messages;
import org.jenkinsci.plugins.scriptler.ScriptlerManagment;
import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.jenkinsci.plugins.scriptler.config.Script;
import org.jenkinsci.plugins.scriptler.config.ScriptlerConfiguration;

/**
 * 
 * @author Dominik Bartholdi (imod)
 * 
 */
public class ScriptHelper {

    private final static Logger LOGGER = Logger.getLogger(ScriptHelper.class.getName());

    /**
     * Loads the script information.
     * 
     * @param id
     *            the id of the script
     * @param withSrc
     *            should the script sources be loaded too?
     * @return the script - <code>null</code> if the id is not set or the script with the given id can not be resolved
     */
    public static Script getScript(String id, boolean withSrc) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        Script s = ScriptlerConfiguration.getConfiguration().getScriptById(id);
        File scriptSrc = new File(ScriptlerManagment.getScriptDirectory(), id);
        if (withSrc) {
            try {
                Reader reader = new FileReader(scriptSrc);
                String src = IOUtils.toString(reader);
                s.setScript(src);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, Messages.scriptSourceNotFound(id), e);
            }
        }
        return s;
    }

    public static String runScript(String[] slaves, String scriptTxt, Parameter[] parameters) throws IOException, ServletException {
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
    public static String runScript(String node, String scriptTxt, Parameter[] parameters) throws IOException, ServletException {

        Object output = "[no output]";
        ByteArrayOutputStream sos = new ByteArrayOutputStream();
        if (node != null && scriptTxt != null) {

            try {
                Computer comp = Hudson.getInstance().getComputer(node);
                if (comp == null && "(master)".equals(node)) {
                    output = MasterComputer.localChannel.call(new GroovyScript(scriptTxt, parameters, false, new StreamTaskListener(sos)));
                } else if (comp == null) {
                    output = Messages.node_not_found(node) + "\n";
                } else {
                    if (comp.getChannel() == null) {
                        output = Messages.node_not_online(node) + "\n";
                    }

                    else {
                        output = comp.getChannel().call(new GroovyScript(scriptTxt, parameters, false, new StreamTaskListener(sos)));
                    }
                }

            } catch (InterruptedException e) {
                throw new ServletException(e);
            }
        }
        return sos.toString();
    }

}
