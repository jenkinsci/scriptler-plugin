package org.jenkinsci.plugins.scriptler.share;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

import java.util.List;

public abstract class ScriptInfoCatalog<T extends ScriptInfo> implements ExtensionPoint {

    public static ExtensionList<ScriptInfoCatalog> all() {
        return ExtensionList.lookup(ScriptInfoCatalog.class);
    }

    public abstract T getEntryById(String id);

    public abstract CatalogInfo getInfo();

    public abstract List<T> getEntries();

    public abstract String getScriptSource(T scriptInfo);

    public abstract String getDisplayName();
}
