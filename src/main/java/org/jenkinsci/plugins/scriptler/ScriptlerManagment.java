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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.jenkinsci.plugins.scriptler.config.Script;
import org.jenkinsci.plugins.scriptler.config.ScriptlerConfiguration;
import org.jenkinsci.plugins.scriptler.git.GitScriptlerRepository;
import org.jenkinsci.plugins.scriptler.share.CatalogInfo;
import org.jenkinsci.plugins.scriptler.share.ScriptInfo;
import org.jenkinsci.plugins.scriptler.share.ScriptInfoCatalog;
import org.jenkinsci.plugins.scriptler.util.ScriptHelper;
import org.jenkinsci.plugins.scriptler.util.UIHelper;
import org.kohsuke.stapler.ForwardToView;
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
    private final static String MASTER = "(master)";
    private final static String ALL = "(all)";
    private final static String ALL_SLAVES = "(all slaves)";

    private boolean isRunScriptPermissionEnabled() {
        return getConfiguration().isAllowRunScriptPermission();
    }

    public Permission getRequiredPermissionForRunScript() {
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
        return "scriptler";
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

    /**
     * save the scriptler 'global' settings (on settings screen, not global Jenkins config)
     * 
     * @param res
     * @param rsp
     * @param disableRemoteCatalog
     * @param allowRunScriptPermission
     * @param allowRunScriptEdit
     * @return
     * @throws IOException
     */
    public HttpResponse doScriptlerSettings(StaplerRequest res, StaplerResponse rsp, @QueryParameter("disableRemoteCatalog") boolean disableRemoteCatalog, @QueryParameter("allowRunScriptPermission") boolean allowRunScriptPermission,
            @QueryParameter("allowRunScriptEdit") boolean allowRunScriptEdit) throws IOException {
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
     * @throws ServletException
     */
    public HttpResponse doDownloadScript(StaplerRequest req, StaplerResponse rsp, @QueryParameter("id") String id, @QueryParameter("catalog") String catalogName) throws IOException, ServletException {
        checkPermission(Hudson.ADMINISTER);

        ScriptlerConfiguration c = getConfiguration();
        if (c.isDisbableRemoteCatalog()) {
            return new HttpRedirect("index");
        }

        for (ScriptInfoCatalog scriptInfoCatalog : ScriptInfoCatalog.all()) {
            if (catalogName.equals(scriptInfoCatalog.getInfo().name)) {
                final ScriptInfo info = scriptInfoCatalog.getEntryById(id);
                final String source = scriptInfoCatalog.getScriptSource(info);
                final List<Parameter> paramList = new ArrayList<Parameter>();
                for (String paramName : info.getParameters()) {
                    paramList.add(new Parameter(paramName, null));
                }

                Parameter[] parameters = paramList.toArray(new Parameter[paramList.size()]);

                final String finalName = saveScriptAndForward(id, info.getName(), info.getComment(), source, false, false, catalogName, id, parameters);
                return new HttpRedirect("editScript?id=" + finalName);
            }
        }
        final ForwardToView view = new ForwardToView(this, "catalog.jelly");
        view.with("message", Messages.download_failed(id, catalogName));
        view.with("catName", catalogName);
        return view;
    }

    /**
     * Saves a script snipplet as file to the system.
     * 
     * @param req
     *            response
     * @param rsp
     *            request
     * @param id
     *            the script id (fileName)
     * @param name
     *            the name of the script
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
     * @throws ServletException
     */
    public HttpResponse doScriptAdd(StaplerRequest req, StaplerResponse rsp, @QueryParameter("id") String id, @QueryParameter("name") String name, @QueryParameter("comment") String comment, @QueryParameter("script") String script,
            @QueryParameter("nonAdministerUsing") boolean nonAdministerUsing, @QueryParameter("onlyMaster") boolean onlyMaster, String originCatalogName, String originId) throws IOException, ServletException {

        checkPermission(Hudson.ADMINISTER);

        Parameter[] parameters = UIHelper.extractParameters(req.getSubmittedForm());

        saveScriptAndForward(id, name, comment, script, nonAdministerUsing, onlyMaster, originCatalogName, originId, parameters);
        return new HttpRedirect("index");
    }

    /**
     * Save the script details and return the forward to index
     * 
     * @return the final name of the saved script - which is also the id of the script!
     * @throws IOException
     */
    private String saveScriptAndForward(String id, String name, String comment, String script, boolean nonAdministerUsing, boolean onlyMaster, String originCatalogName, String originId, Parameter[] parameters) throws IOException {
        script = script == null ? "TODO" : script;
        if (StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException("'id' must not be empty!");
        }

        final String displayName = name == null ? id : name;
        final String finalFileName = fixFileName(originCatalogName, id);

        // save (overwrite) the file/script
        File newScriptFile = new File(getScriptDirectory(), finalFileName);
        Writer writer = new FileWriter(newScriptFile);
        writer.write(script);
        writer.close();

        commitFileToGitRepo(finalFileName);

        Script newScript = null;
        if (!StringUtils.isEmpty(originId)) {
            newScript = new Script(finalFileName, displayName, comment, true, originCatalogName, originId, new SimpleDateFormat("dd MMM yyyy HH:mm:ss a").format(new Date()), parameters);
        } else {
            // save (overwrite) the meta information
            newScript = new Script(finalFileName, displayName, comment, nonAdministerUsing, parameters, onlyMaster);
        }
        ScriptlerConfiguration cfg = getConfiguration();
        cfg.addOrReplace(newScript);
        cfg.save();
        return finalFileName;
    }

    /**
     * adds/commits the given file to the local git repo - file must be written to scripts directory!
     * 
     * @param finalFileName
     * @throws IOException
     */
    private void commitFileToGitRepo(final String finalFileName) throws IOException {
        try {
            getGitRepo().addSingleFileToRepo(finalFileName);
        } catch (Exception e) {
            throw new IOException("failed to update git repo", e);
        }
    }

    private GitScriptlerRepository getGitRepo() {
        return Jenkins.getInstance().getExtensionList(GitScriptlerRepository.class).get(GitScriptlerRepository.class);
    }

    /**
     * Triggers a hard reset on the git repo
     * @return redirects to the repo entry page at <code>http://jenkins.orga.com/scriptler.git</code>
     * @throws IOException
     */
    public HttpResponse doHardResetGit() throws IOException {
        checkPermission(Hudson.ADMINISTER);
        getGitRepo().hardReset();
        return new HttpRedirect("/scriptler.git");
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
    public HttpResponse doRemoveScript(StaplerRequest res, StaplerResponse rsp, @QueryParameter("id") String id) throws IOException {
        checkPermission(Hudson.ADMINISTER);

        // remove the file
        File oldScript = new File(getScriptDirectory(), id);
        oldScript.delete();

        try {
            final GitScriptlerRepository gitRepo = Jenkins.getInstance().getExtensionList(GitScriptlerRepository.class).get(GitScriptlerRepository.class);
            gitRepo.rmSingleFileToRepo(id);
        } catch (Exception e) {
            throw new IOException("failed to update git repo", e);
        }

        // remove the meta information
        ScriptlerConfiguration cfg = getConfiguration();
        cfg.removeScript(id);
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

            commitFileToGitRepo(fileName);

            Script script = ScriptHelper.getScript(fileName, false);
            if (script == null) {
                script = new Script(fileName, fileName, true, nonAdministerUsing, false);
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
    public void doRunScript(StaplerRequest req, StaplerResponse rsp, @QueryParameter("id") String id) throws IOException, ServletException {
        Script script = ScriptHelper.getScript(id, true);
        checkPermission(getRequiredPermissionForRunScript());

        final boolean isAdmin = Jenkins.getInstance().getACL().hasPermission(Jenkins.ADMINISTER);
        final boolean isChangeScriptAllowed = isAdmin || allowRunScriptEdit();

        req.setAttribute("script", script);
        req.setAttribute("readOnly", !isChangeScriptAllowed);
        // set default selection
        req.setAttribute("currentNode", MASTER);
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
     * @param scriptSrc
     *            the script code (groovy)
     * @param node
     *            the node, to execute the code on.
     * @throws IOException
     * @throws ServletException
     */
    public void doTriggerScript(StaplerRequest req, StaplerResponse rsp, @QueryParameter("id") String id, @QueryParameter("script") String scriptSrc, @QueryParameter("node") String node) throws IOException, ServletException {

        checkPermission(getRequiredPermissionForRunScript());

        final Parameter[] parameters = UIHelper.extractParameters(req.getSubmittedForm());

        Script tempScript = null;
        final boolean isAdmin = Jenkins.getInstance().getACL().hasPermission(Jenkins.ADMINISTER);
        final boolean isChangeScriptAllowed = isAdmin || allowRunScriptEdit();

        if (!isChangeScriptAllowed) {
            tempScript = ScriptHelper.getScript(id, true);
            // use original script, user has no permission to change it!s
            scriptSrc = tempScript.script;
        } else {
            // set the script info back to the request, to display it together with
            // the output.
            tempScript = ScriptHelper.getScript(id, false);
            tempScript.setScript(scriptSrc);
        }

        final String[] slaves = resolveSlaveNames(node);
        String output = ScriptHelper.runScript(slaves, scriptSrc, parameters);

        tempScript.setParameters(parameters);// show the same parameters to the user
        req.setAttribute("script", tempScript);
        req.setAttribute("currentNode", node);
        req.setAttribute("output", output);
        req.setAttribute("readOnly", !isChangeScriptAllowed);
        req.getView(this, "runscript.jelly").forward(req, rsp);
    }

    private String[] resolveSlaveNames(String nameAlias) {
        List<String> slaves = null;
        if (nameAlias.equalsIgnoreCase(ALL) || nameAlias.equalsIgnoreCase(ALL_SLAVES)) {
            slaves = this.getSlaveNames();
            if (nameAlias.equalsIgnoreCase(ALL)) {
                if (!slaves.contains(MASTER)) {
                    slaves.add(MASTER);
                }
            }
            if (nameAlias.equalsIgnoreCase(ALL_SLAVES)) {
                // take the master node out of the loop if we said all slaves
                slaves.remove(MASTER);
            }
        } else {
            slaves = Arrays.asList(nameAlias);
        }
        return slaves.toArray(new String[slaves.size()]);
    }

    /**
     * Loads the script by its name and forwards the request to "show.jelly".
     * 
     * @param req
     *            request
     * @param rsp
     *            response
     * @param scriptName
     *            the name of the script to be loaded in to the show view.
     * @throws IOException
     * @throws ServletException
     */
    public void doShowScript(StaplerRequest req, StaplerResponse rsp, @QueryParameter("id") String id) throws IOException, ServletException {
        checkPermission(Hudson.RUN_SCRIPTS);

        Script script = ScriptHelper.getScript(id, true);
        req.setAttribute("script", script);
        req.getView(this, "show.jelly").forward(req, rsp);
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
    public void doEditScript(StaplerRequest req, StaplerResponse rsp, @QueryParameter("id") String id) throws IOException, ServletException {
        checkPermission(Hudson.ADMINISTER);

        Script script = ScriptHelper.getScript(id, true);
        req.setAttribute("script", script);
        req.getView(this, "edit.jelly").forward(req, rsp);
    }

    /**
     * Gets the names of all configured slaves, regardless whether they are online, including alias of ALL and ALL_SLAVES
     * 
     * @return list with all slave names
     */
    public List<String> getSlaveAlias(Script script) {

        if (script.onlyMaster) {
            List<String> slaveNames = new ArrayList<String>();
            slaveNames.add(MASTER);
            return slaveNames;
        }
        final List<String> slaveNames = getSlaveNames();
        // add 'magic' name for master, so all nodes can be handled the same way
        if (!slaveNames.contains(MASTER)) {
            slaveNames.add(0, MASTER);
        }
        if (slaveNames.size() > 0) {
            if (!slaveNames.contains(ALL)) {
                slaveNames.add(1, ALL);
            }
            if (!slaveNames.contains(ALL_SLAVES)) {
                slaveNames.add(2, ALL_SLAVES);
            }
        }
        return slaveNames;
    }

    private List<String> getSlaveNames() {
        ComputerSet computers = Jenkins.getInstance().getComputer();
        List<String> slaveNames = computers.get_slaveNames();

        // slaveNames is unmodifiable, therefore create a new list
        List<String> slaves = new ArrayList<String>();
        slaves.addAll(slaveNames);
        return slaves;
    }

    /**
     * Gets the remote catalogs containing the available scripts for download.
     * 
     * @return the catalog
     */
    public List<ScriptInfoCatalog> getCatalogs() {
        return ScriptInfoCatalog.all();
    }

    public ScriptInfoCatalog<? extends ScriptInfo> getCatalogByName(String catalogName) {
        if (StringUtils.isNotBlank(catalogName)) {
            for (ScriptInfoCatalog<? extends ScriptInfo> sic : getCatalogs()) {
                final CatalogInfo info = sic.getInfo();
                if (catalogName.equals(info.name)) {
                    return sic;
                }
            }
        }
        return null;
    }

    public CatalogInfo getCatalogInfoByName(String catalogName) {
        if (StringUtils.isNotBlank(catalogName)) {
            for (ScriptInfoCatalog<? extends ScriptInfo> sic : getCatalogs()) {
                final CatalogInfo info = sic.getInfo();
                if (catalogName.equals(info.name)) {
                    return info;
                }
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
