package org.jvnet.hudson.plugins.scriptler.util;

import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.Hudson.MasterComputer;
import hudson.util.RemotingDiagnostics;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.apache.commons.io.IOUtils;
import org.jvnet.hudson.plugins.scriptler.Messages;
import org.jvnet.hudson.plugins.scriptler.ScriptlerManagment;
import org.jvnet.hudson.plugins.scriptler.config.Script;
import org.jvnet.hudson.plugins.scriptler.config.ScriptlerConfiguration;

public class ScriptHelper {

	private final static Logger LOGGER = Logger.getLogger(ScriptHelper.class.getName());

	/**
	 * Loads the script information.
	 * 
	 * @param scriptName
	 *            the name of the script
	 * @param withSrc
	 *            should the script sources be loaded too?
	 * @return the script
	 */
	public static Script getScript(String scriptName, boolean withSrc) {
		Script s = ScriptlerConfiguration.getConfiguration().getScriptByName(scriptName);
		File scriptSrc = new File(ScriptlerManagment.getScriptDirectory(), scriptName);
		if (withSrc) {
			try {
				Reader reader = new FileReader(scriptSrc);
				String src = IOUtils.toString(reader);
				s.setScript(src);
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, "not able to load sources for script [" + scriptName + "]", e);
			}
		}
		return s;
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
	public static String doScript(String node, String scriptTxt) throws IOException, ServletException {

		String output = "[no output]";
		if (node != null && scriptTxt != null) {

			try {

				Computer comp = Hudson.getInstance().getComputer(node);
				if (comp == null && "(master)".equals(node)) {
					output = RemotingDiagnostics.executeGroovy(scriptTxt, MasterComputer.localChannel);
				} else if (comp == null) {
					output = Messages.node_not_found(node);
				} else {
					if (comp.getChannel() == null) {
						output = Messages.node_not_online(node);
					}

					else {
						output = RemotingDiagnostics.executeGroovy(scriptTxt, comp.getChannel());
					}
				}

			} catch (InterruptedException e) {
				throw new ServletException(e);
			}
		}
		return output;

	}
}
