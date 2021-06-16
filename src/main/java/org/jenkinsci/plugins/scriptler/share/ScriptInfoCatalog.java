package org.jenkinsci.plugins.scriptler.share;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

import java.util.List;

public interface ScriptInfoCatalog<T extends ScriptInfo> extends ExtensionPoint {

    static ExtensionList<ScriptInfoCatalog> all() {
        return ExtensionList.lookup(ScriptInfoCatalog.class);
    }

    T getEntryById(String id);

    CatalogInfo getInfo();

    List<T> getEntries();

    String getScriptSource(T scriptInfo);

    String getDisplayName();
}
