/**
 *
 */
package org.jenkinsci.plugins.scriptler.git;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.RootAction;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import org.jenkinsci.plugins.scriptler.ScriptlerPermissions;
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
    private static final Logger LOGGER = Logger.getLogger(GitScriptlerRepository.class.getName());

    @Inject
    public SSHD sshd;

    static final String REPOID = "scriptler.git";

    public GitScriptlerRepository() {
        super(ScriptlerManagement.getScriptDirectory2().toFile());
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

    public String getHttpCloneUrl() {
        return Jenkins.get().getRootUrl() + REPOID;
    }

    public String getSshCloneUrl() throws MalformedURLException {
        String hostname = new URL(Objects.requireNonNull(Jenkins.get().getRootUrl())).getHost();
        int port = sshd.getActualPort();
        return "ssh://" + hostname + ":" + port + "/" + REPOID;
    }

    public boolean hasPushPermission() {
        return Jenkins.get().hasPermission(ScriptlerPermissions.CONFIGURE);
    }

    /**
     * @see org.jenkinsci.plugins.gitserver.FileBackedHttpGitRepository#checkPushPermission()
     */
    @Override
    protected void checkPushPermission() {
        Jenkins.get().checkPermission(ScriptlerPermissions.CONFIGURE);
    }

    @Override
    protected void updateWorkspace(Repository repo) throws IOException, GitAPIException {
        super.updateWorkspace(repo);
        final ScriptlerConfiguration cfg =
                ExtensionList.lookupSingleton(ScriptlerManagement.class).getConfiguration();
        SyncUtil.syncDirWithCfg(ScriptlerManagement.getScriptDirectory2(), cfg);
        cfg.save();
    }

    /**
     * adds and commits a single file to this git repo
     *
     * @param fileName
     *            must be relative to repo root dir
     */
    public void addSingleFileToRepo(String fileName) {
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
     */
    public void rmSingleFileToRepo(String fileName) {
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
                return git.reset()
                        .setMode(ResetType.HARD)
                        .setRef("master")
                        .call()
                        .getObjectId()
                        .name();
            } catch (CheckoutConflictException e) {
                throw new IOException("not able to perform a hard reset", e);
            } catch (GitAPIException e) {
                throw new IOException("problem executing reset command", e);
            }
        }
        return "";
    }

    public Collection<LogInfo> getLog() throws IOException {
        Collection<LogInfo> msgs = new ArrayList<>();
        try {
            // TODO find a way to limit the number of log entries - e.g. ..log().addRange(...).call()
            for (RevCommit c : new Git(this.openRepository()).log().call()) {
                msgs.add(new LogInfo(
                        c.getName(),
                        c.getAuthorIdent().getName(),
                        c.getCommitterIdent().getName(),
                        new Date(c.getCommitTime() * 1000L),
                        c.getFullMessage()));
            }
        } catch (NoHeadException e) {
            throw new IOException("not able to retrieve git log", e);
        } catch (GitAPIException e) {
            throw new IOException("problem executing log command", e);
        }
        return msgs;
    }

    public static class LogInfo {
        public final String name, author, committer, msg;
        public final Date commitTime;

        public LogInfo(String name, String author, String committer, Date commitTime, String msg) {
            this.name = name;
            this.author = author;
            this.committer = committer;
            this.commitTime = commitTime;
            this.msg = msg;
        }
    }
}
