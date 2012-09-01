package org.jenkinsci.plugins.scriptler.share.gh;

import hudson.Extension;
import hudson.Util;
import hudson.ProxyConfiguration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.scriptler.share.CatalogInfo;
import org.jenkinsci.plugins.scriptler.share.ScriptInfo;
import org.jenkinsci.plugins.scriptler.share.ScriptInfoCatalog;

/**
 * Provides access to the scriptler scripts shared at https://github.com/jenkinsci/jenkins-scripts
 * 
 * @author Dominik Bartholdi (imod)
 * 
 */
@Extension(ordinal = 10)
public class GHCatalog extends ScriptInfoCatalog<ScriptInfo> {

    private final static Logger LOGGER = Logger.getLogger(GHCatalog.class.getName());

    public static final String REPO_BASE = "https://github.com/jenkinsci/jenkins-scripts/tree/master/scriptler";
    public static final String DOWNLOAD_URL = "https://raw.github.com/jenkinsci/jenkins-scripts/master/scriptler/{1}";

    public static final CatalogInfo CATALOG_INFO = new CatalogInfo("gh", REPO_BASE, REPO_BASE, DOWNLOAD_URL);

    @Override
    public List<ScriptInfo> getEntries() {
        try {
            return Arrays.asList(CentralScriptJsonCatalog.all().get(CentralScriptJsonCatalog.class).toList().list);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "not abe to load script infos from GH", e);
        }
        return Collections.emptyList();
    }

    @Override
    public String getDisplayName() {
        return "GitHub";
    }

    @Override
    public ScriptInfo getEntryById(String id) {
        for (ScriptInfo info : getEntries()) {
            if (id.equals(info.getId())) {
                return info;
            }
        }
        return null;
    }

    @Override
    public CatalogInfo getInfo() {
        return CATALOG_INFO;
    }

    @Override
    public String getScriptSource(ScriptInfo scriptInfo) {

        try {

            final String scriptUrl = CATALOG_INFO.getReplacedDownloadUrl(scriptInfo.getName(), scriptInfo.getId());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Util.copyStreamAndClose(ProxyConfiguration.open(new URL(scriptUrl)).getInputStream(), out);
            return out.toString("UTF-8");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "not abe to load script sources from GH for: " + scriptInfo, e);
        }

        return null;
    }
}
