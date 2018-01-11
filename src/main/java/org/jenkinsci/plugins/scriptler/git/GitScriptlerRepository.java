/**
 * 
 */
package org.jenkinsci.plugins.scriptler.git;

import hudson.Extension;
import hudson.model.RootAction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import jenkins.model.Jenkins;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jenkinsci.main.modules.sshd.SSHD;
import org.jenkinsci.plugins.gitserver.FileBackedHttpGitRepository;
import org.jenkinsci.plugins.scriptler.ScriptlerManagement;
import org.jenkinsci.plugins.scriptler.ScriptlerPluginImpl;
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
        super(ScriptlerManagement.getScriptDirectory());
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
        Jenkins.getInstance().checkPermission(ScriptlerPluginImpl.CONFIGURE);
    }

    @Override
    protected void updateWorkspace(Repository repo) throws IOException, GitAPIException {
        super.updateWorkspace(repo);
        final ScriptlerConfiguration cfg = Jenkins.getInstance().getExtensionList(ScriptlerManagement.class).get(0).getConfiguration();
        SyncUtil.syncDirWithCfg(ScriptlerManagement.getScriptDirectory(), cfg);
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

    public String hardReset() throws IOException {
        final Repository repo = this.openRepository();
        final Git git = new Git(repo);
        if (repo.getRepositoryState().canResetHead()) {
            try {
                return git.reset().setMode(ResetType.HARD).setRef("master").call().getObjectId().name();
            } catch (CheckoutConflictException e) {
                throw new IOException("not able to perform a hard reset", e);
            } catch (GitAPIException e) {
                throw new IOException("problem executing reset command", e);
            }
        }
        return "";
    }

    public Collection<LogInfo> getLog() throws IOException {
        Collection<LogInfo> msgs = new ArrayList<LogInfo>();
        try {
            // TODO find a way to limit the number of log entries - e.g. ..log().addRange(...).call()
            for (RevCommit c : new Git(this.openRepository()).log().call()) {
                msgs.add(new LogInfo(c.getName(), c.getAuthorIdent().getName(), c.getCommitterIdent().getName(), new Date(c.getCommitTime() * 1000), c.getFullMessage()));
            }
        } catch (NoHeadException e) {
            throw new IOException("not able to retrieve git log", e);
        } catch (GitAPIException e) {
            throw new IOException("problem executing log command", e);
        }
        return msgs;
    }

    public static class LogInfo {
        public final String name, author, commiter, msg;
        public final Date committime;

        public LogInfo(String name, String author, String commiter, Date committime, String msg) {
            this.name = name;
            this.author = author;
            this.commiter = commiter;
            this.committime = committime;
            this.msg = msg;
        }
    }
}
