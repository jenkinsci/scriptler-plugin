/**
 *
 */
package org.jenkinsci.plugins.scriptler.git;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.RootAction;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;
import jenkins.model.Jenkins;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jenkinsci.main.modules.sshd.SSHD;
import org.jenkinsci.plugins.gitserver.FileBackedHttpGitRepository;
import org.jenkinsci.plugins.scriptler.ScriptlerManagement;
import org.jenkinsci.plugins.scriptler.ScriptlerPermissions;
import org.jenkinsci.plugins.scriptler.SyncUtil;
import org.jenkinsci.plugins.scriptler.config.ScriptlerConfiguration;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Exposes Git repository at <code>/scriptler.git</code>
 *
 * @author Dominik Bartholdi (imod)
 *
 */
@Extension
public class GitScriptlerRepository extends FileBackedHttpGitRepository implements RootAction {
    private static final Logger LOGGER = Logger.getLogger(GitScriptlerRepository.class.getName());

    private static final int LOG_MAX_COMMITS = 20;
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

    @Restricted(NoExternalUse.class)
    public int getSshdPort() {
        return SSHD.get().getActualPort();
    }

    public String getSshCloneUrl() throws MalformedURLException {
        String hostname = new URL(Objects.requireNonNull(Jenkins.get().getRootUrl())).getHost();
        int port = getSshdPort();
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
        try (Repository r = openRepository()) {
            Git git = new Git(r);
            AddCommand cmd = git.add();
            cmd.addFilepattern(fileName);
            cmd.call();

            CommitCommand co = git.commit();
            co.setAuthor("Scriptler/" + Jenkins.getAuthentication2().getName(), "noreply@jenkins-ci.org");
            co.setMessage("update script via WebUI: " + fileName);
            co.call();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e, () -> "failed to add/commit " + fileName + " into Git repository");
        }
    }

    /**
     * adds and commits a single file to this git repo
     *
     * @param fileName
     *            must be relative to repo root dir
     */
    public void rmSingleFileToRepo(String fileName) {
        try (Repository r = openRepository()) {
            Git git = new Git(r);
            RmCommand cmd = git.rm();
            cmd.addFilepattern(fileName);
            cmd.call();

            CommitCommand co = git.commit();
            co.setAuthor("Scriptler/" + Jenkins.getAuthentication2().getName(), "noreply@jenkins-ci.org");
            co.setMessage("remove script via WebUI: " + fileName);
            co.call();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e, () -> "failed to remove " + fileName + " from Git repository");
        }
    }

    public void hardReset() throws IOException {
        try (Repository r = this.openRepository()) {
            final Git git = new Git(r);
            if (r.getRepositoryState().canResetHead()) {
                try {
                    git.reset().setMode(ResetType.HARD).setRef("master").call();
                } catch (CheckoutConflictException e) {
                    throw new IOException("not able to perform a hard reset", e);
                } catch (GitAPIException e) {
                    throw new IOException("problem executing reset command", e);
                }
            }
        }
    }

    public Collection<LogInfo> getLog() throws IOException {
        try (Repository r = openRepository()) {
            Iterable<RevCommit> commits =
                    new Git(r).log().setMaxCount(LOG_MAX_COMMITS).call();
            return StreamSupport.stream(commits.spliterator(), false)
                    .map(LogInfo::new)
                    .toList();
        } catch (NoHeadException e) {
            throw new IOException("not able to retrieve git log", e);
        } catch (GitAPIException e) {
            throw new IOException("problem executing log command", e);
        }
    }

    public record LogInfo(String name, PersonIdent author, PersonIdent committer, Date commitTime, String msg) {
        public LogInfo(RevCommit c) {
            this(
                    c.getName(),
                    c.getAuthorIdent(),
                    c.getCommitterIdent(),
                    new Date(c.getCommitTime() * 1000L),
                    c.getFullMessage());
        }
    }
}
