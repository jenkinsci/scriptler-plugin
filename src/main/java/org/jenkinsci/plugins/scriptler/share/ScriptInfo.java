package org.jenkinsci.plugins.scriptler.share;

import org.jenkinsci.plugins.scriptler.config.NamedResource;

public class ScriptInfo implements NamedResource {
    public String script;
    public String comment;
    public String core;
    public String name;

    public String getId() {
        return script;
    }

    public String getName() {
        return name;
    }
}
