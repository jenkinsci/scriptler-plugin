/*
 * The MIT License
 *
 * Copyright (c) 2010, Dominik Bartholdi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.plugins.scriptler;

import hudson.Extension;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.model.Hudson;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import hudson.util.RemotingDiagnostics;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jvnet.hudson.plugins.scriptler.config.Script;
import org.jvnet.hudson.plugins.scriptler.config.ScriptlerConfiguration;
import org.jvnet.hudson.plugins.scriptler.share.Catalog;
import org.jvnet.hudson.plugins.scriptler.share.CatalogEntry;
import org.jvnet.hudson.plugins.scriptler.share.ShareManager;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * @author domi
 * 
 */
@Extension
public class ScriptlerManagment extends ManagementLink {

	private final static Logger LOGGER = Logger.getLogger(ScriptlerManagment.class.getName());

	// always retrieve via getter
	private ScriptlerConfiguration cfg = null;

	private ShareManager shareManager = new ShareManager();

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.model.ManagementLink#getIconFileName()
	 */
	@Override
	public String getIconFileName() {
		return "notepad.gif";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.model.ManagementLink#getUrlName()
	 */
	@Override
	public String getUrlName() {
		return "scriptler";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.model.Action#getDisplayName()
	 */
	public String getDisplayName() {
		return Messages.display_name();
	}

	@Override
	public String getDescription() {
		return Messages.description();
	}

	public ScriptlerManagment getScriptler() {
		return this;
	}

	public ScriptlerConfiguration getConfiguration() {
		if (cfg == null) {
			try {
				cfg = ScriptlerConfiguration.load();
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, "Failed to load scriptler configuration", e);
			}
		}
		return cfg;
	}

	public HttpResponse doDownloadScript(StaplerRequest res, StaplerResponse rsp, @QueryParameter("name") String name) throws IOException {

		Catalog catalog = shareManager.loadCatalog();
		CatalogEntry entry = catalog.getEntryByName(name);
		String source = shareManager.downloadScript(name);

		return doScriptAdd(res, rsp, name, entry.comment, source);
	}

	public HttpResponse doScriptAdd(StaplerRequest res, StaplerResponse rsp, @QueryParameter("name") String name, @QueryParameter("comment") String comment,
			@QueryParameter("script") String script) throws IOException {

		if (StringUtils.isEmpty(script) || StringUtils.isEmpty(name)) {
			throw new IllegalArgumentException("name and script must not be empty");
		}
		name = fixFileName(name);

		// save (overwrite) the file/script
		File newScriptFile = new File(getScriptDirectory(), name);
		Writer writer = new FileWriter(newScriptFile);
		writer.write(script);
		writer.close();

		// save (overwrite) the meta information
		Script newScript = new Script(name, comment);
		ScriptlerConfiguration cfg = getConfiguration();
		cfg.addOrReplace(newScript);
		cfg.save();

		return new HttpRedirect("index");
	}

	public HttpResponse doRemoveScript(StaplerRequest res, StaplerResponse rsp, @QueryParameter("name") String name) throws IOException {

		// remove the file
		File oldScript = new File(getScriptDirectory(), name);
		oldScript.delete();

		// remove the meta information
		ScriptlerConfiguration cfg = getConfiguration();
		cfg.removeScript(name);
		cfg.save();

		return new HttpRedirect("index");
	}

	/**
	 * Uploads a script to the defined directory
	 */
	public HttpResponse doUploadScript(StaplerRequest req) throws IOException, ServletException {
		try {
			Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
			File rootDir = getScriptDirectory();

			ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());

			FileItem fileItem = (FileItem) upload.parseRequest(req).get(0);
			String fileName = Util.getFileName(fileItem.getName());
			if (StringUtils.isEmpty(fileName)) {
				return new HttpRedirect(".");
			}
			fileName = fixFileName(fileName);

			fileItem.write(new File(rootDir, fileName));

			Script script = getScript(fileName, false);
			if (script == null) {
				script = new Script(fileName, "uploaded");
			}

			ScriptlerConfiguration config = getConfiguration();
			config.addOrReplace(script);

			return new HttpRedirect("index");
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new ServletException(e);
		}
	}

	protected Script getScript(String scriptName, boolean withSrc) {
		Script s = getConfiguration().getScriptByName(scriptName);
		File scriptSrc = new File(getScriptDirectory(), scriptName);
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
	 * display the screen to trigger a script
	 * 
	 * @param req
	 * @param rsp
	 * @param scriptName
	 * @throws IOException
	 * @throws ServletException
	 */
	public void doRunScript(StaplerRequest req, StaplerResponse rsp, @QueryParameter("name") String scriptName) throws IOException, ServletException {
		Script script = getScript(scriptName, true);
		req.setAttribute("script", script);
		// set default selection
		req.setAttribute("currentNode", "(master)");
		req.getView(this, "runscript.jelly").forward(req, rsp);
	}

	/**
	 * trigger/run/execute the script on a slave and show the result/output
	 * 
	 * @param req
	 * @param rsp
	 * @param scriptName
	 * @throws IOException
	 * @throws ServletException
	 */
	public void doTriggerScript(StaplerRequest req, StaplerResponse rsp, @QueryParameter("scriptName") String scriptName,
			@QueryParameter("script") String script, @QueryParameter("node") String node) throws IOException, ServletException {

		checkPermission(Hudson.ADMINISTER);

		// set the script info back to the request, to display it together with
		// the output
		Script tempScript = getScript(scriptName, false);
		tempScript.setScript(script);
		req.setAttribute("script", tempScript);
		req.setAttribute("currentNode", node);

		String output = doScript(node, script);
		req.setAttribute("output", output);
		req.getView(this, "runscript.jelly").forward(req, rsp);
	}

	/**
	 * runs the execution on a given slave
	 * 
	 * @param req
	 * @param rsp
	 * @param view
	 * @throws IOException
	 * @throws ServletException
	 */
	private String doScript(String node, String scriptTxt) throws IOException, ServletException {

		String output = "[no output]";
		if (node != null && scriptTxt != null) {

			try {

				Computer comp = Hudson.getInstance().getComputer(node);
				if (comp == null) {
					output = Messages.node_not_found(node);
				} else {
					if (comp.getChannel() == null) {
						output = Messages.node_not_online(node);
					} else {
						output = RemotingDiagnostics.executeGroovy(scriptTxt, comp.getChannel());
					}
				}

			} catch (InterruptedException e) {
				throw new ServletException(e);
			}
		}
		return output;

	}

	public void doEditScript(StaplerRequest req, StaplerResponse rsp, @QueryParameter("name") String scriptName) throws IOException, ServletException {
		Script script = getScript(scriptName, true);
		req.setAttribute("script", script);
		req.getView(this, "edit.jelly").forward(req, rsp);
	}

	public List<String> getSlaveNames() {
		ComputerSet computers = Hudson.getInstance().getComputer();
		List<String> slaveNames = computers.get_slaveNames();

		List<String> test = new ArrayList<String>();
		test.addAll(slaveNames);

		if (!test.contains("(master)")) {
			test.add("(master)");
		}
		return test;
	}

	/**
	 * Gets the remote catalog containing the available scripts for download.
	 * 
	 * @return the catalog
	 */
	public Catalog getCatalog() {
		return shareManager.loadCatalog();
	}

	/**
	 * returns the directory where the script files get stored
	 * 
	 * @return the script directory
	 */
	public static File getScriptDirectory() {
		return new File(getScriptlerHomeDirectory(), "scripts");
	}

	public static File getScriptlerHomeDirectory() {
		return new File(Hudson.getInstance().getRootDir(), "scriptler");
	}

	private void checkPermission(Permission permission) {
		Hudson.getInstance().checkPermission(permission);
	}

	private String fixFileName(String name) {
		if (!name.endsWith(".groovy")) {
			name += ".groovy";
		}
		// make sure we don't have spaces in the filename
		name = name.replace(" ", "_").trim();
		return name;
	}
}
