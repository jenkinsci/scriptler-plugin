package org.jvnet.hudson.plugins.scriptler.share;

import java.text.MessageFormat;

import org.apache.commons.lang.StringUtils;

public class CatalogInfo {
    public final String name;
    public final String catalogLocation;
    public final String scriptDownloadUrl;
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
    public CatalogInfo(String name, String catLocation, String scriptDetailUrl, String scriptDownloadUrl) {
        this.name = name;
        this.catalogLocation = catLocation;
        this.scriptDownloadUrl = scriptDownloadUrl;
        this.scriptDetailUrl = scriptDetailUrl;
    }

    public String getReplacedDownloadUrl(String scriptName, String id) {
        if (StringUtils.isEmpty(scriptDownloadUrl)) {
            return null;
        }
        return MessageFormat.format(scriptDownloadUrl.trim(), scriptName, id);
    }

    public String getReplacedDetailUrl(String scriptName, String id) {
        if (StringUtils.isEmpty(scriptDetailUrl)) {
            return null;
        }
        return MessageFormat.format(scriptDetailUrl.trim(), scriptName, id);
    }

}
