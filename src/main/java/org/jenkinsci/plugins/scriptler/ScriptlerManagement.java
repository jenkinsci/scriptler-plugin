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
import hudson.security.AccessControlled;
import hudson.security.Permission;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.apache.commons.fileupload2.core.FileItem;
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

/**
 * Creates the link on the "manage Jenkins" page and handles all the web requests.
 *
 * @author Dominik Bartholdi (imod)
 */
@Extension
public class ScriptlerManagement extends ManagementLink implements RootAction {

    private static final Logger LOGGER = Logger.getLogger(ScriptlerManagement.class.getName());
    private static final String INVALID_PATH = "Invalid file path received: ";
    private static final String INDEX = "index";
    private static final String NOT_APPROVED_YET = "notApprovedYet";
    private static final String CAN_BYPASS_APPROVAL = "canByPassScriptApproval";
    private static final String SCRIPT = "script";
    private static final String MASTER = "(master)";
    private static final String CONTROLLER = "(controller)";
    private static final String ALL = "(all)";
    private static final String ALL_SLAVES = "(all slaves)";
    private static final String ALL_AGENTS = "(all agents)";

    private static final MarkupFormatter INSTANCE = RawHtmlMarkupFormatter.INSTANCE;

    // used in Jelly view
    public Permission getScriptlerRunScripts() {
        return ScriptlerPermissions.RUN_SCRIPTS;
    }

    // used in Jelly view
    public Permission getScriptlerConfigure() {
        return ScriptlerPermissions.CONFIGURE;
    }

    public boolean hasAtLeastOneScriptlerPermission() {
        return Jenkins.get().hasPermission(ScriptlerPermissions.RUN_SCRIPTS)
                || Jenkins.get().hasPermission(ScriptlerPermissions.CONFIGURE);
    }

    public void checkAtLeastOneScriptlerPermission() {
        // to be sure the user has either CONFIGURE or RUN_SCRIPTS permission
        if (!Jenkins.get().hasPermission(ScriptlerPermissions.RUN_SCRIPTS)) {
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
        return getConfiguration().isDisableRemoteCatalog();
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
     */
    @RequirePOST
    public HttpResponse doScriptlerSettings(
            StaplerRequest2 res,
            StaplerResponse2 rsp,
            @QueryParameter("disableRemoteCatalog") boolean disableRemoteCatalog)
            throws IOException {
        checkPermission(ScriptlerPermissions.CONFIGURE);

        ScriptlerConfiguration cfg = getConfiguration();
        cfg.setDisableRemoteCatalog(disableRemoteCatalog);
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
     */
    @RequirePOST
    public HttpResponse doDownloadScript(
            StaplerRequest2 req,
            StaplerResponse2 rsp,
            @QueryParameter("id") String id,
            @QueryParameter("catalog") String catalogName)
            throws IOException {
        checkPermission(ScriptlerPermissions.CONFIGURE);

        ScriptlerConfiguration c = getConfiguration();
        if (c.isDisableRemoteCatalog()) {
            return new HttpRedirect(INDEX);
        }

        for (ScriptInfoCatalog<ScriptInfo> scriptInfoCatalog : getCatalogs()) {
            if (catalogName.equals(scriptInfoCatalog.getInfo().name)) {
                final ScriptInfo info = scriptInfoCatalog.getEntryById(id);
                final String source = scriptInfoCatalog.getScriptSource(scriptInfoCatalog.getEntryById(id));
                final List<Parameter> paramList = new ArrayList<>();
                for (String paramName : info.getParameters()) {
                    paramList.add(new Parameter(paramName, null));
                }

                final String finalName = saveScriptAndForward(
                        id, info.getName(), info.getComment(), source, false, false, catalogName, id, paramList);
                return new HttpRedirect("editScript?id=" + finalName);
            }
        }
        final ForwardToView view = new ForwardToView(this, "catalog.jelly");
        view.with("message", Messages.download_failed(id, catalogName));
        view.with("catName", catalogName);
        return view;
    }

    /**
     * Saves a script snippet as file to the system.
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
     * @param onlyController
     *            this script is only allowed to run on the controller
     * @param originCatalogName
     *            (optional) the name of the catalog the script is loaded/added from
     * @param originId
     *            (optional) the original id the script had at the catalog
     * @return forward to 'index'
     */
    @RequirePOST
    public HttpResponse doScriptAdd(
            StaplerRequest2 req,
            StaplerResponse2 rsp,
            @QueryParameter("id") String id,
            @QueryParameter("name") String name,
            @QueryParameter("comment") String comment,
            @QueryParameter(SCRIPT) String script,
            @QueryParameter("nonAdministerUsing") boolean nonAdministerUsing,
            @QueryParameter("onlyController") boolean onlyController,
            String originCatalogName,
            String originId)
            throws IOException, ServletException {

        checkPermission(ScriptlerPermissions.CONFIGURE);

        List<Parameter> parameters = UIHelper.extractParameters(req.getSubmittedForm());

        saveScriptAndForward(
                id, name, comment, script, nonAdministerUsing, onlyController, originCatalogName, originId, parameters);
        return new HttpRedirect(INDEX);
    }

    /**
     * Save the script details and return the forward to index
     *
     * @return the final name of the saved script - which is also the id of the script!
     */
    private String saveScriptAndForward(
            String id,
            String name,
            String comment,
            String script,
            boolean nonAdministerUsing,
            boolean onlyController,
            String originCatalogName,
            String originId,
            @NonNull List<Parameter> parameters)
            throws IOException {
        script = script == null ? "TODO" : script;
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("'id' must not be empty!");
        }

        final String displayName = name == null ? id : name;
        final String finalFileName = fixFileName(originCatalogName, id);

        // save (overwrite) the file/script
        Path scriptDirectory = getScriptDirectory2();
        Path newScriptFile = scriptDirectory.resolve(finalFileName);

        if (!Util.isDescendant(scriptDirectory.toFile(), newScriptFile.toFile())) {
            LOGGER.log(
                    Level.WARNING,
                    "Folder traversal detected, file path received: {0}, after fixing: {1}",
                    new Object[] {id, finalFileName});
            throw new IOException(INVALID_PATH + id);
        }

        ScriptHelper.writeScriptToFile(newScriptFile, script);

        commitFileToGitRepo(finalFileName);

        ScriptHelper.putScriptInApprovalQueueIfRequired(script);

        final Script newScript;
        if (originId != null && !originId.isEmpty()) {
            newScript = new Script(
                    finalFileName,
                    displayName,
                    comment,
                    true,
                    originCatalogName,
                    originId,
                    new SimpleDateFormat("dd MMM yyyy HH:mm:ss a").format(new Date()),
                    parameters);
        } else {
            // save (overwrite) the meta information
            newScript = new Script(finalFileName, displayName, comment, nonAdministerUsing, parameters, onlyController);
        }
        ScriptlerConfiguration cfg = getConfiguration();
        cfg.addOrReplace(newScript);
        cfg.save();
        return finalFileName;
    }

    /**
     * adds/commits the given file to the local git repo - file must be written to scripts directory!
     */
    private void commitFileToGitRepo(final String finalFileName) {
        getGitRepo().addSingleFileToRepo(finalFileName);
    }

    private GitScriptlerRepository getGitRepo() {
        return ExtensionList.lookupSingleton(GitScriptlerRepository.class);
    }

    /**
     * Triggers a hard reset on the git repo
     * @return redirects to the repo entry page at <code>/scriptler.git</code>
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
     */
    @RequirePOST
    public HttpResponse doRemoveScript(StaplerRequest2 res, StaplerResponse2 rsp, @QueryParameter("id") String id)
            throws IOException {
        checkPermission(ScriptlerPermissions.CONFIGURE);

        // remove the file
        Path scriptDirectory = getScriptDirectory2();
        Path oldScript = scriptDirectory.resolve(id);
        if (!Util.isDescendant(scriptDirectory.toFile(), oldScript.toFile())) {
            LOGGER.log(
                    Level.WARNING,
                    "Folder traversal detected, file path received: {0}, after fixing: {1}",
                    new Object[] {id, oldScript});
            throw new Failure(INVALID_PATH + id);
        }
        try {
            Files.delete(oldScript);
        } catch (IOException e) {
            Failure failure = new Failure("not able to delete " + oldScript);
            failure.initCause(e);
            throw failure;
        }

        try {
            getGitRepo().rmSingleFileToRepo(id);
        } catch (IllegalStateException e) {
            throw new IOException("failed to update git repo", e);
        }

        // remove the meta information
        ScriptlerConfiguration cfg = getConfiguration();
        cfg.removeScript(id);
        cfg.save();

        return new HttpRedirect(INDEX);
    }

    /**
     * Uploads a script and stores it with the given filename to the configuration. It will be stored on the filessytem.
     *
     * @param req
     *            request
     * @return forward to index page.
     */
    @RequirePOST
    public HttpResponse doUploadScript(StaplerRequest2 req) throws IOException, ServletException {
        checkPermission(ScriptlerPermissions.CONFIGURE);
        try {

            FileItem<?> fileItem = req.getFileItem2("file");
            boolean nonAdministerUsing = req.getSubmittedForm().getBoolean("nonAdministerUsing");
            String fileName = Util.getFileName(fileItem.getName());
            if (fileName.isEmpty()) {
                return new HttpRedirect(".");
            }
            saveScript(fileItem, nonAdministerUsing, fileName);

            return new HttpRedirect(INDEX);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    /**
     * Protected only for testing
     */
    /*private*/ void saveScript(FileItem<?> fileItem, boolean nonAdministerUsing, String fileName) throws IOException {
        // upload can only be to/from local catalog
        String fixedFileName = fixFileName(null, fileName);

        Path fixedFile;
        try {
            fixedFile = Paths.get(fixedFileName);
        } catch (InvalidPathException e) {
            throw new IOException(INVALID_PATH + fileName, e);
        }

        if (fixedFile.isAbsolute()) {
            LOGGER.log(
                    Level.WARNING,
                    "Folder traversal detected, file path received: {0}, after fixing: {1}. Seems to be an attempt to use absolute path instead of relative one",
                    new Object[] {fileName, fixedFileName});
            throw new IOException(INVALID_PATH + fileName);
        }

        Path rootDir = getScriptDirectory2();
        final Path f = rootDir.resolve(fixedFileName);

        if (!Util.isDescendant(rootDir.toFile(), f.toFile())) {
            LOGGER.log(
                    Level.WARNING,
                    "Folder traversal detected, file path received: {0}, after fixing: {1}. Seems to be an attempt to use folder escape.",
                    new Object[] {fileName, fixedFileName});
            throw new IOException(INVALID_PATH + fileName);
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
     */
    public void doRunScript(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter("id") String id)
            throws IOException, ServletException {
        checkPermission(ScriptlerPermissions.RUN_SCRIPTS);

        Script script = ScriptHelper.getScript(id, true);
        if (script == null) {
            // TODO check if we cannot do better here
            throw new IOException(Messages.scriptNotFound(id));
        }
        if (script.getScriptText() == null) {
            req.setAttribute("scriptNotFound", true);
        } else {
            boolean canByPassScriptApproval = Jenkins.get().hasPermission(ScriptlerPermissions.BYPASS_APPROVAL);

            // we do not want user with approval right to auto-approve script when landing on that page
            if (!ScriptHelper.isApproved(script.getScriptText(), false)) {
                req.setAttribute(NOT_APPROVED_YET, true);
            }

            req.setAttribute(CAN_BYPASS_APPROVAL, canByPassScriptApproval);
        }

        req.setAttribute(SCRIPT, script);
        // set default selection
        req.setAttribute("currentNode", CONTROLLER);
        req.getView(this, "runScript.jelly").forward(req, rsp);
    }

    /**
     * Trigger/run/execute the script on an agent and show the result/output. The request then gets forward to <code>runScript.jelly</code> (This is usually also where the request came from). The
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
     *            the node to execute the code on.
     */
    @RequirePOST
    public void doTriggerScript(
            StaplerRequest2 req,
            StaplerResponse2 rsp,
            @QueryParameter("id") String id,
            @QueryParameter(SCRIPT) String scriptSrc,
            @QueryParameter("node") String node)
            throws IOException, ServletException {
        checkPermission(ScriptlerPermissions.RUN_SCRIPTS);

        final List<Parameter> parameters = UIHelper.extractParameters(req.getSubmittedForm());

        boolean canByPassScriptApproval = Jenkins.get().hasPermission(ScriptlerPermissions.BYPASS_APPROVAL);

        // set the script info back to the request, to display it together with the output.
        Script originalScript = ScriptHelper.getScript(id, true);
        if (originalScript == null) {
            rsp.sendError(404, "No script found for id=" + id);
            return;
        }

        String originalScriptSourceCode = originalScript.getScriptText();

        Script tempScript = originalScript.copy();
        if (originalScriptSourceCode != null && originalScriptSourceCode.equals(scriptSrc)) {
            // not copied by default
            tempScript.setScriptText(originalScriptSourceCode);
        } else {
            tempScript.setScriptText(scriptSrc);
            ScriptHelper.putScriptInApprovalQueueIfRequired(scriptSrc);
        }

        String output;
        if (ScriptHelper.isApproved(scriptSrc)) {
            List<String> computers = resolveComputerNames(node);
            output = ScriptHelper.runScript(computers, scriptSrc, parameters);
        } else {
            LOGGER.log(
                    Level.WARNING,
                    "Script {0} was not approved yet, consider asking your administrator to approve it.",
                    id);
            output = null;
            req.setAttribute(NOT_APPROVED_YET, true);
        }

        tempScript.setParameters(parameters); // show the same parameters to the user
        req.setAttribute(SCRIPT, tempScript);
        req.setAttribute("currentNode", node);
        req.setAttribute("output", output);
        req.setAttribute(CAN_BYPASS_APPROVAL, canByPassScriptApproval);
        req.getView(this, "runScript.jelly").forward(req, rsp);
    }

    /**
     * Trigger/run/execute the script on an agent and directly forward the result/output to the response.
     *
     * @param req
     *            request
     * @param rsp
     *            response
     * @param script
     *            the script code (groovy)
     * @param node
     *            the node, to execute the code on, defaults to {@value #CONTROLLER}
     * @param contentType
     *            the contentType to use in the response, defaults to text/plain
     */
    @RequirePOST
    public void doRun(
            StaplerRequest2 req,
            StaplerResponse2 rsp,
            @QueryParameter(fixEmpty = true) String script,
            @QueryParameter(fixEmpty = true) String node,
            @QueryParameter(fixEmpty = true) String contentType)
            throws IOException, ServletException {

        checkPermission(ScriptlerPermissions.RUN_SCRIPTS);

        String id = req.getRestOfPath();
        if (id.startsWith("/")) {
            id = id.substring(1);
        }

        if (id.isEmpty()) {
            throw new IOException("Please specify a script id. Use /scriptler/run/<yourScriptId>");
        }

        Script tempScript = ScriptHelper.getScript(id, true);

        if (tempScript == null) {
            throw new IOException("Unknown script: " + id + ". Use /scriptler/run/<yourScriptId>");
        }

        if (script == null) {
            // use original script
            script = tempScript.getScriptText();
        }

        if (!ScriptHelper.isApproved(script)) {
            LOGGER.log(
                    Level.WARNING,
                    "Script {0} was not approved yet, consider asking your administrator to approve it.",
                    id);
            rsp.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Script not approved yet, consider asking your administrator to approve it.");
            return;
        }

        Collection<Parameter> paramArray = prepareParameters(req, tempScript);

        rsp.setContentType(contentType == null ? "text/plain" : contentType);

        final List<String> computers = resolveComputerNames(node == null ? CONTROLLER : node);
        if (computers.size() > 1) {
            rsp.getOutputStream().print(ScriptHelper.runScript(computers, script, paramArray));
        } else {
            rsp.getOutputStream().print(ScriptHelper.runScript(computers.get(0), script, paramArray));
        }
    }

    @NonNull
    private Collection<Parameter> prepareParameters(StaplerRequest2 req, Script tempScript) {
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

    private List<String> resolveComputerNames(String nameAlias) {
        final List<String> computers;
        if (nameAlias.equalsIgnoreCase(ALL)
                || nameAlias.equalsIgnoreCase(ALL_AGENTS)
                || nameAlias.equalsIgnoreCase(ALL_SLAVES)) {
            computers = getComputerNames();
            if (nameAlias.equalsIgnoreCase(ALL)) {
                computers.add(CONTROLLER);
            }
        } else if (nameAlias.equalsIgnoreCase(MASTER)) {
            computers = List.of(CONTROLLER);
        } else {
            computers = List.of(nameAlias);
        }
        return computers;
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
     */
    public void doShowScript(
            StaplerRequest2 req, StaplerResponse2 rsp, @AncestorInPath Item item, @QueryParameter("id") String id)
            throws IOException, ServletException {
        // action directly accessible to any people configuring job, so use a more lenient permission check
        Jenkins jenkins = Jenkins.get();
        if (!jenkins.hasAnyPermission(ScriptlerPermissions.RUN_SCRIPTS, ScriptlerPermissions.CONFIGURE)) {
            AccessControlled parent = item == null ? jenkins : item;
            parent.checkPermission(Item.CONFIGURE);
        }
        Script script = ScriptHelper.getScript(id, true);
        req.setAttribute(SCRIPT, script);
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
     */
    public void doEditScript(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter("id") String id)
            throws IOException, ServletException {
        checkPermission(ScriptlerPermissions.CONFIGURE);

        Script script = ScriptHelper.getScript(id, true);
        if (script == null || script.getScriptText() == null) {
            req.setAttribute("scriptNotFound", true);
        } else {
            boolean canByPassScriptApproval = Jenkins.get().hasPermission(ScriptlerPermissions.BYPASS_APPROVAL);

            // we do not want user with approval right to auto-approve script when landing on that page
            if (!ScriptHelper.isApproved(script.getScriptText(), false)) {
                req.setAttribute(NOT_APPROVED_YET, true);
            }

            req.setAttribute(CAN_BYPASS_APPROVAL, canByPassScriptApproval);
        }

        req.setAttribute(SCRIPT, script);
        req.getView(this, "edit.jelly").forward(req, rsp);
    }

    /**
     * @deprecated Use {@link #getComputerAliases(Script)} instead.
     */
    @Deprecated(since = "381")
    public List<String> getSlaveAlias(Script script) {
        return getComputerAliases(script);
    }

    /**
     * Gets the names of all configured computers, regardless whether they are online, including alias of ALL and ALL_AGENTS
     *
     * @return list with all computer names
     */
    public List<String> getComputerAliases(Script script) {
        if (script.onlyController) {
            return List.of(CONTROLLER);
        }
        final List<String> computerNames = getComputerNames();
        // add 'magic' name for the controller, so all nodes can be handled the same way
        computerNames.addAll(0, List.of(CONTROLLER, ALL, ALL_AGENTS));
        return computerNames;
    }

    private List<String> getComputerNames() {
        return Arrays.stream(Jenkins.get().getComputers())
                // remove the controller's computer as it has an empty name
                .filter(Predicate.not(Jenkins.MasterComputer.class::isInstance))
                .map(Computer::getName)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Gets the remote catalogs containing the available scripts for download.
     *
     * @return the catalog
     */
    public List<ScriptInfoCatalog<ScriptInfo>> getCatalogs() {
        return ScriptInfoCatalog.all();
    }

    public ScriptInfoCatalog<ScriptInfo> getCatalogByName(String catalogName) {
        if (catalogName != null && !catalogName.isBlank()) {
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
        if (catalogName != null && !catalogName.isBlank()) {
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
     * @deprecated Use {@link #getScriptDirectory2()} instead.
     */
    @Deprecated(since = "380")
    public static File getScriptDirectory() {
        return new File(getScriptlerHomeDirectory(), "scripts");
    }

    /**
     * returns the directory where the script files get stored
     *
     * @return the script directory
     */
    public static Path getScriptDirectory2() {
        return getScriptlerHomeDirectory2().resolve("scripts");
    }

    /**
     * @deprecated Use {@link #getScriptlerHomeDirectory2()} instead.
     */
    @Deprecated(since = "380")
    public static File getScriptlerHomeDirectory() {
        return getScriptlerHomeDirectory2().toFile();
    }

    public static Path getScriptlerHomeDirectory2() {
        return Jenkins.get().getRootDir().toPath().resolve("scriptler");
    }

    private void checkPermission(Permission permission) {
        Jenkins.get().checkPermission(permission);
    }

    private String fixFileName(String catalogName, String name) {
        if (!name.endsWith(".groovy")) {
            if (catalogName != null && !catalogName.isEmpty()) {
                name += "." + catalogName;
            }
            name += ".groovy";
        }
        // make sure we don't have spaces in the filename
        name = name.replace(" ", "_").trim();
        LOGGER.log(Level.FINE, "set file name to: {0}", name);
        return name;
    }
}
