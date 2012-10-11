/**
 * 
 */
package org.jenkinsci.plugins.scriptler.git;

import hudson.Extension;
import hudson.model.RootAction;

import java.io.IOException;

import javax.inject.Inject;

import jenkins.model.Jenkins;

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

    @Inject
    public SSHD sshd;

    static final String REPOID = "scriptler.git";

    public GitScriptlerRepository() {
        super(ScriptlerManagment.getGitScriptDirectory());
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
        SyncUtil.syncDirWithCfg(ScriptlerManagment.getGitScriptDirectory().getName(), ScriptlerManagment.getGitScriptDirectory(), cfg);
        cfg.save();
    }
}
