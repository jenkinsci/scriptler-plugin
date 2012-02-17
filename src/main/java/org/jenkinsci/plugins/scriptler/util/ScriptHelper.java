package org.jenkinsci.plugins.scriptler.util;

import groovy.lang.GroovyShell;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.remoting.DelegatingCallable;
import hudson.util.RemotingDiagnostics;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import jenkins.model.Jenkins.MasterComputer;

import org.apache.commons.io.IOUtils;
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
     * @return the script
     */
    public static Script getScript(String id, boolean withSrc) {
        Script s = ScriptlerConfiguration.getConfiguration().getScriptById(id);
        File scriptSrc = new File(ScriptlerManagment.getScriptDirectory(), id);
        if (withSrc) {
            try {
                Reader reader = new FileReader(scriptSrc);
                String src = IOUtils.toString(reader);
                s.setScript(src);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "not able to load sources for script [" + id + "]", e);
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

        String output = "[no output]";
        if (node != null && scriptTxt != null) {

            try {

                Computer comp = Hudson.getInstance().getComputer(node);
                if (comp == null && "(master)".equals(node)) {
                    output = RemotingDiagnostics.executeGroovy(scriptTxt, MasterComputer.localChannel);
                } else if (comp == null) {
                    output = Messages.node_not_found(node) + "\n";
                } else {
                    if (comp.getChannel() == null) {
                        output = Messages.node_not_online(node) + "\n";
                    }

                    else {
                        // output = RemotingDiagnostics.executeGroovy(scriptTxt, comp.getChannel());
                        output = comp.getChannel().call(new GroovyScript(scriptTxt, parameters));
                    }
                }

            } catch (InterruptedException e) {
                throw new ServletException(e);
            }
        }
        return output;
    }

    /**
     * Inspired by hudson.util.RemotingDiagnostics.Script, but adding parameters.
     */
    private static final class GroovyScript implements DelegatingCallable<String, RuntimeException> {
        private static final long serialVersionUID = 1L;
        private final String script;
        private final Parameter[] parameters;
        private transient ClassLoader cl;

        private GroovyScript(String script, Parameter[] parameters) {
            this.script = script;
            this.parameters = parameters;
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
                if ("out".equals(paramName)) {
                    pw.write("skipping parameter 'out' this name is used inernal, please rename!");
                } else {
                    shell.setVariable(paramName, param.getValue());
                }
            }
            shell.setVariable("out", pw);
            try {
                Object output = shell.evaluate(script);
                if (output != null) {
                    pw.println("Result: " + output);
                }
            } catch (Throwable t) {
                t.printStackTrace(pw);
            }
            return out.toString();
        }
    }

}
