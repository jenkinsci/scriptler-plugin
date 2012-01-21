package org.jenkinsci.plugins.scriptler.share.scriptlerweb;

import org.jenkinsci.plugins.scriptler.share.ScriptInfo;

public class CatalogEntry extends ScriptInfo {

    public final String id;
    public final String provider;
    public final String url;

    public CatalogEntry(String id, String name, String comment, String provider, String url) {
        this.id = id;
        this.name = name;
        this.comment = comment;
        this.provider = provider;
        this.url = url;
    }

}
