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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.markup.MarkupFormatter;
import hudson.markup.RawHtmlMarkupFormatter;
import hudson.model.*;
import hudson.security.Permission;
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
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates the link on the "manage Jenkins" page and handles all the web requests.
 * 
 * @author Dominik Bartholdi (imod)
 */
@Extension
public class ScriptlerManagement extends ManagementLink implements RootAction {

    private final static Logger LOGGER = Logger.getLogger(ScriptlerManagement.class.getName());
    private final static String MASTER = "(master)";
    private final static String ALL = "(all)";
    private final static String ALL_SLAVES = "(all slaves)";

    private static final MarkupFormatter INSTANCE = RawHtmlMarkupFormatter.INSTANCE;

    // used in Jelly view
    public Permission getScriptlerRunScripts() {
        return ScriptlerPermissions.RUN_SCRIPTS;
    }
    
    // used in Jelly view
    public Permission getScriptlerConfigure() {
        return ScriptlerPermissions.CONFIGURE;
    }
    
    public boolean hasAtLeastOneScriptlerPermission(){
        return Jenkins.get().hasPermission(ScriptlerPermissions.RUN_SCRIPTS) || Jenkins.get().hasPermission(ScriptlerPermissions.CONFIGURE);
    }
    
    public void checkAtLeastOneScriptlerPermission(){
        // to be sure the user has either CONFIGURE or RUN_SCRIPTS permission
        if(!Jenkins.get().hasPermission(ScriptlerPermissions.RUN_SCRIPTS)){
            Jenkins.get().checkPermission(ScriptlerPermissions.CONFIGURE);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see hudson.model.ManagementLink#getIconFileName()
     */
    @Override
    public String getIconFileName() {
        return hasAtLeastOneScriptlerPermission() ? "symbol-file-tray-stacked-outline plugin-ionicons-api" : null;
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.CONFIGURATION;
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

    public ScriptlerManagement getScriptler() {
        return this;
    }

    public ScriptlerConfiguration getConfiguration() {
        return ScriptlerConfiguration.getConfiguration();
    }

    public MarkupFormatter getMarkupFormatter() {
        return INSTANCE;
    }

    /**
     * save the scriptler 'global' settings (on settings screen, not global Jenkins config)
     * 
     * @param res
     * @param rsp
     * @param disableRemoteCatalog
     * @return
     * @throws IOException
     */
    @RequirePOST
    public HttpResponse doScriptlerSettings(StaplerRequest res, StaplerResponse rsp, @QueryParameter("disableRemoteCatalog") boolean disableRemoteCatalog) throws IOException {
        checkPermission(ScriptlerPermissions.CONFIGURE);

        ScriptlerConfiguration cfg = getConfiguration();
        cfg.setDisbableRemoteCatalog(disableRemoteCatalog);
        cfg.save();

        return new HttpRedirect("settings");
    }

    /**
     * Downloads a script from a catalog and imports it to the local system.
     * 
     * @param req
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
    @RequirePOST
    public HttpResponse doDownloadScript(StaplerRequest req, StaplerResponse rsp, @QueryParameter("id") String id, @QueryParameter("catalog") String catalogName) throws IOException {
        checkPermission(ScriptlerPermissions.CONFIGURE);

        ScriptlerConfiguration c = getConfiguration();
        if (c.isDisbableRemoteCatalog()) {
            return new HttpRedirect("index");
        }

        for (ScriptInfoCatalog<ScriptInfo> scriptInfoCatalog : getCatalogs()) {
            if (catalogName.equals(scriptInfoCatalog.getInfo().name)) {
                final ScriptInfo info = scriptInfoCatalog.getEntryById(id);
                final String source = scriptInfoCatalog.getScriptSource(scriptInfoCatalog.getEntryById(id));
                final List<Parameter> paramList = new ArrayList<>();
                for (String paramName : info.getParameters()) {
                    paramList.add(new Parameter(paramName, null));
                }

                final String finalName = saveScriptAndForward(id, info.getName(), info.getComment(), source, false, false, catalogName, id, paramList);
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
     * @param nonAdministerUsing
     *            allow usage in Scriptler build step
     * @param onlyMaster
     *            this script is only allwoed to run on the master node
     * @param originCatalogName
     *            (optional) the name of the catalog the script is loaded/added from
     * @param originId
     *            (optional) the original id the script had at the catalog
     * @return forward to 'index'
     * @throws IOException
     * @throws ServletException
     */
    @RequirePOST
    public HttpResponse doScriptAdd(StaplerRequest req, StaplerResponse rsp, @QueryParameter("id") String id, @QueryParameter("name") String name, @QueryParameter("comment") String comment, @QueryParameter("script") String script,
            @QueryParameter("nonAdministerUsing") boolean nonAdministerUsing, @QueryParameter("onlyMaster") boolean onlyMaster, String originCatalogName, String originId) throws IOException, ServletException {

        checkPermission(ScriptlerPermissions.CONFIGURE);

        List<Parameter> parameters = UIHelper.extractParameters(req.getSubmittedForm());

        saveScriptAndForward(id, name, comment, script, nonAdministerUsing, onlyMaster, originCatalogName, originId, parameters);
        return new HttpRedirect("index");
    }

    /**
     * Save the script details and return the forward to index
     * 
     * @return the final name of the saved script - which is also the id of the script!
     * @throws IOException
     */
    private String saveScriptAndForward(String id, String name, String comment, String script, boolean nonAdministerUsing, boolean onlyMaster, String originCatalogName, String originId, @NonNull List<Parameter> parameters) throws IOException {
        script = script == null ? "TODO" : script;
        if (StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException("'id' must not be empty!");
        }

        final String displayName = name == null ? id : name;
        final String finalFileName = fixFileName(originCatalogName, id);

        // save (overwrite) the file/script
        File newScriptFile = new File(getScriptDirectory(), finalFileName);
    
        if(!Util.isDescendant(getScriptDirectory(), newScriptFile)) {
            LOGGER.log(Level.WARNING, "Folder traversal detected, file path received: {0}, after fixing: {1}", new Object[]{id, finalFileName});
            throw new IOException("Invalid file path received: " + id);
        }

        ScriptHelper.writeScriptToFile(newScriptFile, script);

        commitFileToGitRepo(finalFileName);

        ScriptHelper.putScriptInApprovalQueueIfRequired(script);
        
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
     */
    private void commitFileToGitRepo(final String finalFileName) {
        getGitRepo().addSingleFileToRepo(finalFileName);
    }

    private GitScriptlerRepository getGitRepo() {
        return ExtensionList.lookupSingleton(GitScriptlerRepository.class);
    }

    /**
     * Triggers a hard reset on the git repo
     * @return redirects to the repo entry page at <code>http://jenkins.orga.com/scriptler.git</code>
     * @throws IOException
     */
    @RequirePOST
    public HttpResponse doHardResetGit() throws IOException {
        checkPermission(ScriptlerPermissions.CONFIGURE);
        getGitRepo().hardReset();
        return new HttpRedirect("../scriptler.git");
    }

    /**
     * Removes a script from the config and filesystem.
     * 
     * @param res
     *            response
     * @param rsp
     *            request
     * @param id
     *            the id of the file to be removed
     * @return forward to 'index'
     * @throws IOException
     */
    @RequirePOST
    public HttpResponse doRemoveScript(StaplerRequest res, StaplerResponse rsp, @QueryParameter("id") String id) throws IOException {
        checkPermission(ScriptlerPermissions.CONFIGURE);

        // remove the file
        File scriptDirectory = getScriptDirectory();
        File oldScript = new File(scriptDirectory, id);
        if (!Util.isDescendant(scriptDirectory, oldScript)) {
            LOGGER.log(Level.WARNING, "Folder traversal detected, file path received: {0}, after fixing: {1}", new Object[]{id, oldScript});
            throw new Failure("Invalid file path received: " + id);
        }
        if(!oldScript.delete() && oldScript.exists()) {
            throw new Failure("not able to delete " + oldScript.getAbsolutePath());
        }

        try {
            final GitScriptlerRepository gitRepo = ExtensionList.lookupSingleton(GitScriptlerRepository.class);
            gitRepo.rmSingleFileToRepo(id);
        } catch (IllegalStateException e) {
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
    @RequirePOST
    public HttpResponse doUploadScript(StaplerRequest req) throws IOException, ServletException {
        checkPermission(ScriptlerPermissions.CONFIGURE);
        try {
            

            FileItem fileItem = req.getFileItem("file");
            boolean nonAdministerUsing = req.getSubmittedForm().getBoolean("nonAdministerUsing");
            String fileName = Util.getFileName(fileItem.getName());
            if (StringUtils.isEmpty(fileName)) {
                return new HttpRedirect(".");
            }
            saveScript(fileItem, nonAdministerUsing, fileName);

            return new HttpRedirect("index");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    /**
     * Protected only for testing
     */
    /*private*/ void saveScript(FileItem fileItem, boolean nonAdministerUsing, String fileName) throws Exception {
        // upload can only be to/from local catalog
        String fixedFileName = fixFileName(null, fileName);

        File fixedFile = new File(fixedFileName);

        if(fixedFile.isAbsolute()){
            LOGGER.log(Level.WARNING, "Folder traversal detected, file path received: {0}, after fixing: {1}. Seems to be an attempt to use absolute path instead of relative one", new Object[]{fileName, fixedFileName});
            throw new IOException("Invalid file path received: " + fileName);
        }
        
        File rootDir = getScriptDirectory();
        final File f = new File(rootDir, fixedFileName);
        
        if(!Util.isDescendant(rootDir, new File(rootDir,fixedFileName))) {
            LOGGER.log(Level.WARNING, "Folder traversal detected, file path received: {0}, after fixing: {1}. Seems to be an attempt to use folder escape.", new Object[]{fileName, fixedFileName});
            throw new IOException("Invalid file path received: " + fileName);
        }
        
        fileItem.write(f);

        commitFileToGitRepo(fixedFileName);

        Script script = ScriptHelper.getScript(fixedFileName, false);
        if (script == null) {
            script = new Script(fixedFileName, fixedFileName, true, nonAdministerUsing, false);
        }

        String scriptSource = ScriptHelper.readScriptFromFile(f);
        ScriptHelper.putScriptInApprovalQueueIfRequired(scriptSource);

        ScriptlerConfiguration config = getConfiguration();
        config.addOrReplace(script);
    }

    /**
     * Display the screen to trigger a script. The source of the script get loaded from the filesystem and placed in the request to display it on the page before execution.
     * 
     * @param req
     *            request
     * @param rsp
     *            response
     * @param id
     *            the id of the script to be executed
     * @throws IOException
     * @throws ServletException
     */
    public void doRunScript(StaplerRequest req, StaplerResponse rsp, @QueryParameter("id") String id) throws IOException, ServletException {
        checkPermission(ScriptlerPermissions.RUN_SCRIPTS);

        Script script = ScriptHelper.getScript(id, true);
        if(script == null) {
            //TODO check if we cannot do better here
            throw new IOException(Messages.scriptNotFound(id));
        }
        if(script.script == null){
            req.setAttribute("scriptNotFound", true);
        }else{
            boolean canByPassScriptApproval = Jenkins.get().hasPermission(Jenkins.RUN_SCRIPTS);
        
            // we do not want user with approval right to auto-approve script when landing on that page
            if(!ScriptHelper.isApproved(script.script, false)){
                req.setAttribute("notApprovedYet", true);
            }
        
            req.setAttribute("canByPassScriptApproval", canByPassScriptApproval);
        }

        req.setAttribute("script", script);
        // set default selection
        req.setAttribute("currentNode", MASTER);
        req.getView(this, "runScript.jelly").forward(req, rsp);
    }

    /**
     * Trigger/run/execute the script on a slave and show the result/output. The request then gets forward to <code>runScript.jelly</code> (This is usually also where the request came from). The
     * script passed to this method gets restored in the request again (and not loaded from the system). This way one is able to modify the script before execution and reuse the modified version for
     * further executions.
     * 
     * @param req
     *            request
     * @param rsp
     *            response
     * @param id
     *            the id of the script
     * @param scriptSrc
     *            the script code (groovy)
     * @param node
     *            the node, to execute the code on.
     * @throws IOException
     * @throws ServletException
     */
    @RequirePOST
    public void doTriggerScript(StaplerRequest req, StaplerResponse rsp, @QueryParameter("id") String id, @QueryParameter("script") String scriptSrc, @QueryParameter("node") String node) throws IOException, ServletException {
        checkPermission(ScriptlerPermissions.RUN_SCRIPTS);

        final List<Parameter> parameters = UIHelper.extractParameters(req.getSubmittedForm());

        boolean canByPassScriptApproval = Jenkins.get().hasPermission(Jenkins.RUN_SCRIPTS);

        // set the script info back to the request, to display it together with the output.
        Script originalScript = ScriptHelper.getScript(id, true);
        if(originalScript == null){
            rsp.sendError(404, "No script found for id=" + id);
            return;
        }

        String originalScriptSourceCode = originalScript.script;

        Script tempScript = originalScript.copy();
        if(originalScriptSourceCode != null && originalScriptSourceCode.equals(scriptSrc)){
            // not copied by default
            tempScript.setScript(originalScriptSourceCode);
        }else{
            tempScript.setScript(scriptSrc);
            ScriptHelper.putScriptInApprovalQueueIfRequired(scriptSrc);
        }

        String output;
        if(ScriptHelper.isApproved(scriptSrc)){
            String[] slaves = resolveSlaveNames(node);
            output = ScriptHelper.runScript(slaves, scriptSrc, parameters);
        }else{
            LOGGER.log(Level.WARNING, "Script {0} was not approved yet, consider asking your administrator to approve it.", id);
            output = null;
            req.setAttribute("notApprovedYet", true);
        }

        tempScript.setParameters(parameters);// show the same parameters to the user
        req.setAttribute("script", tempScript);
        req.setAttribute("currentNode", node);
        req.setAttribute("output", output);
        req.setAttribute("canByPassScriptApproval", canByPassScriptApproval);
        req.getView(this, "runScript.jelly").forward(req, rsp);
    }
    
    /**
     * Trigger/run/execute the script on a slave and directly forward the result/output to the response.
     * 
     * @param req
     *            request
     * @param rsp
     *            response
     * @param script
     *            the script code (groovy)
     * @param node
     *            the node, to execute the code on, defaults to {@value #MASTER}
     * @param contentType
     *            the contentType to use in the response, defaults to text/plain
     * @throws IOException
     * @throws ServletException
     */
    @RequirePOST
    public void doRun(StaplerRequest req, StaplerResponse rsp, @QueryParameter(fixEmpty = true) String script,
            @QueryParameter(fixEmpty = true) String node, @QueryParameter(fixEmpty = true) String contentType)
            throws IOException, ServletException {

        checkPermission(ScriptlerPermissions.RUN_SCRIPTS);

        String id = req.getRestOfPath();
        if (id.startsWith("/")) {
            id = id.substring(1);
        }

        if (StringUtils.isEmpty(id)) {
            throw new RuntimeException("Please specify a script id. Use /scriptler/run/<yourScriptId>");
        }

        Script tempScript = ScriptHelper.getScript(id, true);

        if (tempScript == null) {
            throw new RuntimeException("Unknown script: " + id + ". Use /scriptler/run/<yourScriptId>");
        }

        if (script == null) {
            // use original script
            script = tempScript.script;
        }

        if(!ScriptHelper.isApproved(script)){
            LOGGER.log(Level.WARNING, "Script {0} was not approved yet, consider asking your administrator to approve it.", id);
            rsp.sendError(HttpServletResponse.SC_FORBIDDEN, "Script not approved yet, consider asking your administrator to approve it.");
            return;
        }
        
        Collection<Parameter> paramArray = prepareParameters(req, tempScript);

        rsp.setContentType(contentType == null ? "text/plain" : contentType);

        final String[] slaves = resolveSlaveNames(node == null ? MASTER : node);
        if (slaves.length > 1) {
            rsp.getOutputStream().print(ScriptHelper.runScript(slaves, script, paramArray));
        }
        else {
            rsp.getOutputStream().print(ScriptHelper.runScript(slaves[0], script, paramArray));
        }
    }

    @NonNull
    private Collection<Parameter> prepareParameters(StaplerRequest req, Script tempScript) {
        // retain default parameter values
        Map<String, Parameter> params = new HashMap<>();
        for (Parameter param : tempScript.getParameters()) {
            params.put(param.getName(), param);
        }

        // overwrite parameters that are passed as parameters
        for (Map.Entry<String, String[]> param : req.getParameterMap().entrySet()) {
            if (params.containsKey(param.getKey())) {
                params.put(param.getKey(), new Parameter(param.getKey(), param.getValue()[0]));
            }
        }
        return params.values();
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
            slaves = Collections.singletonList(nameAlias);
        }
        return slaves.toArray(new String[0]);
    }

    /**
     * Loads the script by its name and forwards the request to "show.jelly".
     * 
     * @param req
     *            request
     * @param rsp
     *            response
     * @param id
     *            the id of the script to be loaded in to the show view.
     * @throws IOException
     * @throws ServletException
     */
    public void doShowScript(StaplerRequest req, StaplerResponse rsp, @QueryParameter("id") String id) throws IOException, ServletException {
        // action directly accessible to any people configuring job, so no permission check
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
     * @param id
     *            the id of the script to be loaded in to the edit view.
     * @throws IOException
     * @throws ServletException
     */
    public void doEditScript(StaplerRequest req, StaplerResponse rsp, @QueryParameter("id") String id) throws IOException, ServletException {
        checkPermission(ScriptlerPermissions.CONFIGURE);
    
        Script script = ScriptHelper.getScript(id, true);
        if(script == null || script.script == null){
            req.setAttribute("scriptNotFound", true);
        }else{
            boolean canByPassScriptApproval = Jenkins.get().hasPermission(Jenkins.RUN_SCRIPTS);
        
            // we do not want user with approval right to auto-approve script when landing on that page
            if(!ScriptHelper.isApproved(script.script, false)){
                req.setAttribute("notApprovedYet", true);
            }
    
            req.setAttribute("canByPassScriptApproval", canByPassScriptApproval);
        }
        
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
            List<String> slaveNames = new ArrayList<>();
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
        Computer[] computers = Jenkins.get().getComputers();
        List<String> slaves = new ArrayList<>();
        for (Computer c : computers) {
            slaves.add(c.getName());
        }
        return slaves;
    }

    /**
     * Gets the remote catalogs containing the available scripts for download.
     * 
     * @return the catalog
     */
    public List<? extends ScriptInfoCatalog<ScriptInfo>> getCatalogs() {
        return ScriptInfoCatalog.all();
    }

    public ScriptInfoCatalog<? extends ScriptInfo> getCatalogByName(String catalogName) {
        if (StringUtils.isNotBlank(catalogName)) {
            for (ScriptInfoCatalog<ScriptInfo> sic : getCatalogs()) {
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
            for (ScriptInfoCatalog<ScriptInfo> sic : getCatalogs()) {
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
        return new File(Jenkins.get().getRootDir(), "scriptler");
    }

    private void checkPermission(Permission permission) {
        Jenkins.get().checkPermission(permission);
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
