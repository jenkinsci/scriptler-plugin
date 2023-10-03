package org.jenkinsci.plugins.scriptler;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.TransientActionFactory;

@Extension
public class TransientActionProvider extends TransientActionFactory<Job> {
    @Override
    public Class<Job> type() {
        return Job.class;
    }

    @NonNull
    @Override
    public Collection<? extends Action> createFor(@NonNull Job target) {
        return Collections.singleton(new ScriptlerManagement() {
            @Override
            public String getIconFileName() {
                return null;
            }

            @Override
            public String getDisplayName() {
                return null;
            }
        });
    }
}
