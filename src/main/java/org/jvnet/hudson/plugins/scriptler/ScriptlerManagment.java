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
import hudson.Util;
import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.model.Hudson;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import hudson.util.RemotingDiagnostics;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
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
import org.jvnet.hudson.plugins.scriptler.share.CatalogInfo;
import org.jvnet.hudson.plugins.scriptler.share.CatalogManager;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Creates the link on the "manage hudson" page and handles all the web
 * requests.
 * 
 * @author domi
 * 
 */
@Extension
public class ScriptlerManagment extends ManagementLink {

	private final static Logger LOGGER = Logger.getLogger(ScriptlerManagment.class.getName());

	// always retrieve via getter
	private ScriptlerConfiguration cfg = null;

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

	/**
	 * Downloads a script from a catalog and imports it to the local system.
	 * 
	 * @param res
	 *            request
	 * @param rsp
	 *            response
	 * @param name
	 *            the name of the file to be downloaded
	 * @param catalogName
	 *            the catalog to download the file from
	 * @return same forward as from <code>doScriptAdd</code>
	 * @throws IOException
	 */
	public HttpResponse doDownloadScript(StaplerRequest res, StaplerResponse rsp, @QueryParameter("name") String name,
			@QueryParameter("catalog") String catalogName) throws IOException {
		checkPermission(Hudson.ADMINISTER);

		CatalogInfo catInfo = getConfiguration().getCatalogInfo(catalogName);
		CatalogManager catalogManager = new CatalogManager(catInfo);
		Catalog catalog = catalogManager.loadCatalog();
		CatalogEntry entry = catalog.getEntryByName(name);
		String source = catalogManager.downloadScript(name);

		return doScriptAdd(res, rsp, name, entry.comment, source);
	}

	/**
	 * Saves a script snipplet as file to the system.
	 * 
	 * @param res
	 *            response
	 * @param rsp
	 *            request
	 * @param name
	 *            the name for the file
	 * @param comment
	 *            a comment
	 * @param script
	 *            script code
	 * @return forward to 'index'
	 * @throws IOException
	 */
	public HttpResponse doScriptAdd(StaplerRequest res, StaplerResponse rsp, @QueryParameter("name") String name, @QueryParameter("comment") String comment,
			@QueryParameter("script") String script) throws IOException {
		checkPermission(Hudson.ADMINISTER);

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

	/**
	 * Removes a script from the config and filesystem.
	 * 
	 * @param res
	 *            response
	 * @param rsp
	 *            request
	 * @param name
	 *            the name of the file to be removed
	 * @return forward to 'index'
	 * @throws IOException
	 */
	public HttpResponse doRemoveScript(StaplerRequest res, StaplerResponse rsp, @QueryParameter("name") String name) throws IOException {
		checkPermission(Hudson.ADMINISTER);

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
	 * Uploads a script and stores it with the given filename to the
	 * configuration. It will be stored on the filessytem.
	 * 
	 * @param req
	 *            request
	 * @return forward to index page.
	 * @throws IOException
	 * @throws ServletException
	 */
	public HttpResponse doUploadScript(StaplerRequest req) throws IOException, ServletException {
		checkPermission(Hudson.ADMINISTER);
		try {
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

	/**
	 * Loads the script information.
	 * 
	 * @param scriptName
	 *            the name of the script
	 * @param withSrc
	 *            should the script sources be loaded too?
	 * @return the script
	 */
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
	 * Display the screen to trigger a script. The source of the script get
	 * loaded from the filesystem and placed in the request to display it on the
	 * page before execution.
	 * 
	 * @param req
	 *            request
	 * @param rsp
	 *            response
	 * @param scriptName
	 *            the name of the script to be executed
	 * @throws IOException
	 * @throws ServletException
	 */
	public void doRunScript(StaplerRequest req, StaplerResponse rsp, @QueryParameter("name") String scriptName) throws IOException, ServletException {
		checkPermission(Hudson.ADMINISTER);

		Script script = getScript(scriptName, true);
		req.setAttribute("script", script);
		// set default selection
		req.setAttribute("currentNode", "(master)");
		req.getView(this, "runscript.jelly").forward(req, rsp);
	}

	/**
	 * Trigger/run/execute the script on a slave and show the result/output. The
	 * request then gets forward to <code>runscript.jelly</code> (This is
	 * usually also where the request came from). The script passed to this
	 * method gets restored in the request again (and not loaded from the
	 * system). This way one is able to modify the script before execution and
	 * reuse the modified version for further executions.
	 * 
	 * @param req
	 *            request
	 * @param rsp
	 *            response
	 * @param scriptName
	 *            the name of the script
	 * @param script
	 *            the script code (groovy)
	 * @param node
	 *            the node, to execute the code on.
	 * @throws IOException
	 * @throws ServletException
	 */
	public void doTriggerScript(StaplerRequest req, StaplerResponse rsp, @QueryParameter("scriptName") String scriptName,
			@QueryParameter("script") String script, @QueryParameter("node") String node) throws IOException, ServletException {
		checkPermission(Hudson.ADMINISTER);

		// set the script info back to the request, to display it together with
		// the output.
		Script tempScript = getScript(scriptName, false);
		tempScript.setScript(script);
		req.setAttribute("script", tempScript);
		req.setAttribute("currentNode", node);

		String output = doScript(node, script);
		req.setAttribute("output", output);
		req.getView(this, "runscript.jelly").forward(req, rsp);
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

	/**
	 * Loads the script by its name and forwards the request to "edit.jelly".
	 * 
	 * @param req
	 *            request
	 * @param rsp
	 *            response
	 * @param scriptName
	 *            the name of the script to be loaded in to the edit view.
	 * @throws IOException
	 * @throws ServletException
	 */
	public void doEditScript(StaplerRequest req, StaplerResponse rsp, @QueryParameter("name") String scriptName) throws IOException, ServletException {
		checkPermission(Hudson.ADMINISTER);

		Script script = getScript(scriptName, true);
		req.setAttribute("script", script);
		req.getView(this, "edit.jelly").forward(req, rsp);
	}

	/**
	 * Gets the names of all configured slaves, regardless whether they are
	 * online.
	 * 
	 * @return list with all slave names
	 */
	@SuppressWarnings("deprecation")
	public List<String> getSlaveNames() {
		ComputerSet computers = Hudson.getInstance().getComputer();
		List<String> slaveNames = computers.get_slaveNames();

		// slaveNames is unmodifiable, therefore create a new list
		List<String> test = new ArrayList<String>();
		test.addAll(slaveNames);

		// add 'magic' name for master, so all nodes can be handled the same way
		if (!test.contains("(master)")) {
			test.add("(master)");
		}
		return test;
	}

	/**
	 * Gets the remote catalogs containing the available scripts for download.
	 * 
	 * @return the catalog
	 */
	public List<Catalog> getCatalogs() {
		List<Catalog> catalogs = new ArrayList<Catalog>();
		List<CatalogInfo> catalogInfos = getConfiguration().getCatalogInfos();
		for (CatalogInfo catalogInfo : catalogInfos) {
			CatalogManager mgr = new CatalogManager(catalogInfo);
			Catalog catalog = mgr.loadCatalog();
			// as catalogInfo is marked as transient, we have to set it
			catalog.setInfo(catalogInfo);
			if (catalog != null) {
				catalogs.add(catalog);
			}
		}
		return catalogs;
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
