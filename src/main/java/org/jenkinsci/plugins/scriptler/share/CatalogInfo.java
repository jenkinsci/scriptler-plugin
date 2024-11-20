package org.jenkinsci.plugins.scriptler.share;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.text.MessageFormat;

public class CatalogInfo {

    @NonNull
    public final String name;

    @NonNull
    public final String catalogLocation;

    @NonNull
    public final String scriptDownloadUrl;

    @NonNull
    public final String scriptDetailUrl;

    /**
     * Holds the informations used to connect to a catalog location
     *
     * @param name
     *            symbolic name of the catalog, must be unique.
     * @param catLocation
     *            where to download the catalog file from (including file name, e.g. <code>http://myserver.com/scriptler/my-scriptler-catalog.xml</code> )
     * @param scriptDownloadUrl
     *            the url to download a script by its name. Use <code>{0}</code> to mark the position for the file name in the url (e.g. <code>http://myserver.com/scriptler/{0}</code>)
     */
    public CatalogInfo(
            @NonNull String name,
            @NonNull String catLocation,
            @NonNull String scriptDetailUrl,
            @NonNull String scriptDownloadUrl) {
        this.name = name;
        this.catalogLocation = catLocation;
        this.scriptDownloadUrl = scriptDownloadUrl;
        this.scriptDetailUrl = scriptDetailUrl;
    }

    public String getReplacedDownloadUrl(String scriptName, String id) {
        if (scriptDownloadUrl.isEmpty()) {
            return null;
        }
        return MessageFormat.format(scriptDownloadUrl.trim(), scriptName, id);
    }

    public String getReplacedDetailUrl(String scriptName, String id) {
        if (scriptDetailUrl.isEmpty()) {
            return null;
        }
        return MessageFormat.format(scriptDetailUrl.trim(), scriptName, id);
    }
}
