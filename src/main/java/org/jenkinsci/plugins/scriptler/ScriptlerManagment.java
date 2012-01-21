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
package org.jenkinsci.plugins.scriptler;

import hudson.Extension;
import hudson.Util;
import hudson.PluginWrapper;
import hudson.model.ManagementLink;
import hudson.model.RootAction;
import hudson.model.ComputerSet;
import hudson.model.Hudson;
import hudson.security.Permission;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.scriptler.config.Script;
import org.jenkinsci.plugins.scriptler.config.ScriptlerConfiguration;
import org.jenkinsci.plugins.scriptler.share.CatalogInfo;
import org.jenkinsci.plugins.scriptler.share.ScriptInfo;
import org.jenkinsci.plugins.scriptler.share.ScriptInfoCatalog;
import org.jenkinsci.plugins.scriptler.util.ScriptHelper;
import org.jvnet.hudson.plugins.scriptler.Messages;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Creates the link on the "manage Jenkins" page and handles all the web requests.
 * 
 * @author Dominik Bartholdi (imod)
 */
@Extension
public class ScriptlerManagment extends ManagementLink implements RootAction {

    private final static Logger LOGGER = Logger.getLogger(ScriptlerManagment.class.getName());

    private boolean isRunScriptPermissionEnabled() {
        return getConfiguration().isAllowRunScriptPermission();
    }

    private Permission getRequiredPermissionForRunScript() {
        return isRunScriptPermissionEnabled() ? Jenkins.RUN_SCRIPTS : Jenkins.ADMINISTER;
    }

    /*
     * (non-Javadoc)
     * 
     * @see hudson.model.ManagementLink#getIconFileName()
     */
    @Override
    public String getIconFileName() {
        return Jenkins.getInstance().hasPermission(getRequiredPermissionForRunScript()) ? "notepad.gif" : null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see hudson.model.ManagementLink#getUrlName()
     */
    @Override
    public String getUrlName() {
        return Jenkins.getInstance().hasPermission(getRequiredPermissionForRunScript()) ? "scriptler" : null;
    }

    public boolean disableRemoteCatalog() {
        return getConfiguration().isDisbableRemoteCatalog();
    }

    public boolean allowRunScriptEdit() {
        return getConfiguration().isAllowRunScriptEdit();
    }

    public boolean allowRunScriptPermission() {
        return getConfiguration().isAllowRunScriptPermission();
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
        return ScriptlerConfiguration.getConfiguration();
    }

    public String getPluginResourcePath() {
        PluginWrapper wrapper = Hudson.getInstance().getPluginManager().getPlugin(ScritplerPluginImpl.class);
        return Hudson.getInstance().getRootUrl() + "plugin/" + wrapper.getShortName() + "/";
    }

    public HttpResponse doScriptlerSettings(StaplerRequest res, StaplerResponse rsp, @QueryParameter("disableRemoteCatalog") boolean disableRemoteCatalog,
            @QueryParameter("allowRunScriptPermission") boolean allowRunScriptPermission, @QueryParameter("allowRunScriptEdit") boolean allowRunScriptEdit)
            throws IOException {
        checkPermission(Hudson.ADMINISTER);

        ScriptlerConfiguration cfg = getConfiguration();
        cfg.setDisbableRemoteCatalog(disableRemoteCatalog);
        cfg.setAllowRunScriptEdit(allowRunScriptEdit);
        cfg.setAllowRunScriptPermission(allowRunScriptPermission);
        cfg.save();

        return new HttpRedirect("settings");
    }

    /**
     * Downloads a script from a catalog and imports it to the local system.
     * 
     * @param res
     *            request
     * @param rsp
     *            response
     * @param id
     *            the id of the file to be downloaded
     * @param catalogName
     *            the catalog to download the file from
     * @return same forward as from <code>doScriptAdd</code>
     * @throws IOException
     */
    public HttpResponse doDownloadScript(StaplerRequest res, StaplerResponse rsp, @QueryParameter("id") String id, @QueryParameter("catalog") String catalogName)
            throws IOException {
        checkPermission(Hudson.ADMINISTER);

        ScriptlerConfiguration c = getConfiguration();
        if (c.isDisbableRemoteCatalog()) {
            return new HttpRedirect("index");
        }

        ScriptInfo info = null;
        String source = null;
        for (ScriptInfoCatalog scriptInfoCatalog : ScriptInfoCatalog.all()) {
            if (catalogName.equals(scriptInfoCatalog.getInfo().name)) {
                info = scriptInfoCatalog.getEntryById(id);
                source = scriptInfoCatalog.getScriptSource(info);
                break;
            }
        }

        return doScriptAdd(res, rsp, info.name, info.comment, source, false, catalogName, id);
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
     * @param catalogName
     *            (optional) the name of the catalog the script is loaded/added from
     * @param originId
     *            (optional) the original id the script had at the catalog
     * @return forward to 'index'
     * @throws IOException
     */
    public HttpResponse doScriptAdd(StaplerRequest res, StaplerResponse rsp, @QueryParameter("name") String name, @QueryParameter("comment") String comment,
            @QueryParameter("script") String script, @QueryParameter("nonAdministerUsing") boolean nonAdministerUsing, String originCatalogName, String originId)
            throws IOException {
        checkPermission(Hudson.ADMINISTER);

        if (StringUtils.isEmpty(script) || StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("'name' and 'script' must not be empty!");
        }
        name = fixFileName(originCatalogName, name);

        // save (overwrite) the file/script
        File newScriptFile = new File(getScriptDirectory(), name);
        Writer writer = new FileWriter(newScriptFile);
        writer.write(script);
        writer.close();

        Script newScript = null;
        if (!StringUtils.isEmpty(originId)) {
            newScript = new Script(name, comment, true, originCatalogName, originId, new SimpleDateFormat("dd MMM yyyy HH:mm:ss a").format(new Date()));
        } else {
            // save (overwrite) the meta information
            newScript = new Script(name, comment, nonAdministerUsing);
        }
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
     * Uploads a script and stores it with the given filename to the configuration. It will be stored on the filessytem.
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

            FileItem fileItem = req.getFileItem("file");
            boolean nonAdministerUsing = req.getSubmittedForm().getBoolean("nonAdministerUsing");
            String fileName = Util.getFileName(fileItem.getName());
            if (StringUtils.isEmpty(fileName)) {
                return new HttpRedirect(".");
            }
            // upload can only be to/from local catalog
            fileName = fixFileName(null, fileName);

            fileItem.write(new File(rootDir, fileName));

            Script script = ScriptHelper.getScript(fileName, false);
            if (script == null) {
                script = new Script(fileName, "uploaded");
            }
            script.setNonAdministerUsing(nonAdministerUsing);
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
     * Display the screen to trigger a script. The source of the script get loaded from the filesystem and placed in the request to display it on the page before execution.
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
        Script script = ScriptHelper.getScript(scriptName, true);
        checkPermission(getRequiredPermissionForRunScript());

        final boolean isAdmin = Jenkins.getInstance().getACL().hasPermission(Jenkins.ADMINISTER);
        final boolean isChangeScriptAllowed = isAdmin || allowRunScriptEdit();

        req.setAttribute("script", script);
        req.setAttribute("readOnly", !isChangeScriptAllowed);
        // set default selection
        req.setAttribute("currentNode", "(master)");
        req.getView(this, "runscript.jelly").forward(req, rsp);
    }

    /**
     * Trigger/run/execute the script on a slave and show the result/output. The request then gets forward to <code>runscript.jelly</code> (This is usually also where the request came from). The
     * script passed to this method gets restored in the request again (and not loaded from the system). This way one is able to modify the script before execution and reuse the modified version for
     * further executions.
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

        checkPermission(getRequiredPermissionForRunScript());

        Script tempScript = null;
        final boolean isAdmin = Jenkins.getInstance().getACL().hasPermission(Jenkins.ADMINISTER);
        final boolean isChangeScriptAllowed = isAdmin || allowRunScriptEdit();

        if (!isChangeScriptAllowed) {
            tempScript = ScriptHelper.getScript(scriptName, true);
            req.setAttribute("script", tempScript);
            // use original script, user has no permission to change it!s
            script = tempScript.script;
        } else {
            // set the script info back to the request, to display it together with
            // the output.
            tempScript = ScriptHelper.getScript(scriptName, false);
            tempScript.setScript(script);
            req.setAttribute("script", tempScript);
        }

        req.setAttribute("currentNode", node);

        String output = ScriptHelper.doScript(node, script);
        req.setAttribute("output", output);
        req.getView(this, "runscript.jelly").forward(req, rsp);
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

        Script script = ScriptHelper.getScript(scriptName, true);
        req.setAttribute("script", script);
        req.getView(this, "edit.jelly").forward(req, rsp);
    }

    /**
     * Gets the names of all configured slaves, regardless whether they are online.
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
    public List<ScriptInfoCatalog> getCatalogs() {
        return ScriptInfoCatalog.all();
    }

    public CatalogInfo getCatalogInfoByName(String catalogName) {
        List<CatalogInfo> catalogInfos = getConfiguration().getCatalogInfos();
        for (CatalogInfo catalogInfo : catalogInfos) {
            if (catalogInfo.name.equals(catalogName)) {
                return catalogInfo;
            }
        }
        return null;
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

    private String fixFileName(String catalogName, String name) {
        if (!name.endsWith(".groovy")) {
            if (!StringUtils.isEmpty(catalogName)) {
                name += "." + catalogName;
            }
            name += ".groovy";
        }
        // make sure we don't have spaces in the filename
        name = name.replace(" ", "_").trim();
        LOGGER.fine("set file name to: " + name);
        return name;
    }
}
