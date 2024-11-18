package org.jenkinsci.plugins.scriptler.share;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.util.ArrayList;
import java.util.List;

public interface ScriptInfoCatalog<T extends ScriptInfo> extends ExtensionPoint {

    @SuppressWarnings({"rawtypes", "unchecked"}) // unfortunate but necessary given ExtensionList's API
    static List<? extends ScriptInfoCatalog<ScriptInfo>> all() {
        ExtensionList<ScriptInfoCatalog> extensions = ExtensionList.lookup(ScriptInfoCatalog.class);
        List<ScriptInfoCatalog<ScriptInfo>> typedExtensions = new ArrayList<>();
        for (ScriptInfoCatalog catalog : extensions) {
            typedExtensions.add(catalog);
        }
        return typedExtensions;
    }

    T getEntryById(String id);

    CatalogInfo getInfo();

    List<T> getEntries();

    String getScriptSource(T scriptInfo);

    String getDisplayName();
}
