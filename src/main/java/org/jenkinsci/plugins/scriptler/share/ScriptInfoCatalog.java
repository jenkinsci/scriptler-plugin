package org.jenkinsci.plugins.scriptler.share;

import hudson.ExtensionList;

import java.util.List;

import jenkins.model.Jenkins;

import org.apache.tools.ant.ExtensionPoint;

public abstract class ScriptInfoCatalog<T extends ScriptInfo> extends ExtensionPoint {

    public static ExtensionList<ScriptInfoCatalog> all() {
        return Jenkins.getInstance().getExtensionList(ScriptInfoCatalog.class);
    }

    public abstract T getEntryById(String id);

    public abstract CatalogInfo getInfo();

    public abstract List<T> getEntries();

    public abstract String getScriptSource(T scriptInfo);
}
