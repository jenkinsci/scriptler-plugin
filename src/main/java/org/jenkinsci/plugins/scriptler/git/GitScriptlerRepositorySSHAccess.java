/**
 * 
 */
package org.jenkinsci.plugins.scriptler.git;

import hudson.Extension;

import java.io.IOException;

import javax.inject.Inject;

import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;
import org.jenkinsci.plugins.gitserver.RepositoryResolver;

/**
 * Exposes this repository over SSH.
 * 
 * @author Dominik Bartholdi (imod)
 * 
 */
@Extension
public class GitScriptlerRepositorySSHAccess extends RepositoryResolver {

    @Inject
    GitScriptlerRepository repo;

    /**
     * @see org.jenkinsci.plugins.gitserver.RepositoryResolver#createReceivePack(java.lang.String)
     */
    @Override
    public ReceivePack createReceivePack(String fullRepositoryName) throws IOException {
        if (isMine(fullRepositoryName))
            return repo.createReceivePack(repo.openRepository());
        return null;
    }

    /**
     * @see org.jenkinsci.plugins.gitserver.RepositoryResolver#createUploadPack(java.lang.String)
     */
    @Override
    public UploadPack createUploadPack(String fullRepositoryName) throws IOException {
        if (isMine(fullRepositoryName))
            return new UploadPack(repo.openRepository());
        return null;
    }

    private boolean isMine(String name) {
        // Depending on the Git URL the client uses, we may or may not get leading '/'.
        // For example, server:userContent.git vs ssh://server/scriptler.git
        if (name.startsWith("/"))
            name = name.substring(1);
        return name.equals(GitScriptlerRepository.REPOID);
    }
}
