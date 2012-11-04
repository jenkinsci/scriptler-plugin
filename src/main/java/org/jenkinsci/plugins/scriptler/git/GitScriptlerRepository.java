/**
 * 
 */
package org.jenkinsci.plugins.scriptler.git;

import hudson.Extension;
import hudson.model.RootAction;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import jenkins.model.Jenkins;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.main.modules.sshd.SSHD;
import org.jenkinsci.plugins.gitserver.FileBackedHttpGitRepository;
import org.jenkinsci.plugins.scriptler.ScriptlerManagment;
import org.jenkinsci.plugins.scriptler.SyncUtil;
import org.jenkinsci.plugins.scriptler.config.ScriptlerConfiguration;

/**
 * Exposes Git repository at http://server/jenkins/scriptler.git
 * 
 * @author Dominik Bartholdi (imod)
 * 
 */
@Extension
public class GitScriptlerRepository extends FileBackedHttpGitRepository implements RootAction {
    private final static Logger LOGGER = Logger.getLogger(GitScriptlerRepository.class.getName());

    @Inject
    public SSHD sshd;

    static final String REPOID = "scriptler.git";

    public GitScriptlerRepository() {
        super(ScriptlerManagment.getScriptDirectory());
    }

    /**
     * @see hudson.model.Action#getDisplayName()
     */
    public String getDisplayName() {
        return null;
    }

    /**
     * @see hudson.model.Action#getIconFileName()
     */
    public String getIconFileName() {
        return null;
    }

    /**
     * @see hudson.model.Action#getUrlName()
     */
    public String getUrlName() {
        return REPOID;
    }

    /**
     * @see org.jenkinsci.plugins.gitserver.FileBackedHttpGitRepository#checkPushPermission()
     */
    @Override
    protected void checkPushPermission() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
    }

    @Override
    protected void updateWorkspace(Repository repo) throws IOException, GitAPIException {
        super.updateWorkspace(repo);
        final ScriptlerConfiguration cfg = Jenkins.getInstance().getExtensionList(ScriptlerManagment.class).get(0).getConfiguration();
        SyncUtil.syncDirWithCfg(ScriptlerManagment.getScriptDirectory(), cfg);
        cfg.save();
    }

    /**
     * adds and commits a single file to this git repo
     * 
     * @param fileName
     *            must be relative to repo root dir
     * @throws Exception
     *             if an exception occurred
     */
    public void addSingleFileToRepo(String fileName) throws Exception {
        try {
            Git git = new Git(this.openRepository());
            AddCommand cmd = git.add();
            cmd.addFilepattern(fileName);
            cmd.call();

            CommitCommand co = git.commit();
            co.setAuthor("Scriptler/" + Jenkins.getAuthentication().getName(), "noreply@jenkins-ci.org");
            co.setMessage("update script via WebUI: " + fileName);
            co.call();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "failed to add/commit " + fileName + " into Git repository", e);
        }
    }

    /**
     * adds and commits a single file to this git repo
     * 
     * @param fileName
     *            must be relative to repo root dir
     * @throws Exception
     *             if an exception occurred
     */
    public void rmSingleFileToRepo(String fileName) throws Exception {
        try {
            Git git = new Git(this.openRepository());
            RmCommand cmd = git.rm();
            cmd.addFilepattern(fileName);
            cmd.call();

            CommitCommand co = git.commit();
            co.setAuthor("Scriptler/" + Jenkins.getAuthentication().getName(), "noreply@jenkins-ci.org");
            co.setMessage("remove script via WebUI: " + fileName);
            co.call();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "failed to remove " + fileName + " from Git repository", e);
        }
    }
}
