package org.jenkinsci.plugins.scriptler.share.gh;

import hudson.Extension;
import hudson.ProxyConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.scriptler.share.CatalogInfo;
import org.jenkinsci.plugins.scriptler.share.ScriptInfo;
import org.jenkinsci.plugins.scriptler.share.ScriptInfoCatalog;

import javax.annotation.CheckForNull;

/**
 * Provides access to the scriptler scripts shared at https://github.com/jenkinsci/jenkins-scripts
 * 
 * @author Dominik Bartholdi (imod)
 * 
 */
@Extension(ordinal = 10)
public class GHCatalog implements ScriptInfoCatalog<ScriptInfo> {

    private final static Logger LOGGER = Logger.getLogger(GHCatalog.class.getName());

    public static final String REPO_BASE = "https://github.com/jenkinsci/jenkins-scripts/blob/master/scriptler/{1}";
    public static final String DOWNLOAD_URL = "https://raw.github.com/jenkinsci/jenkins-scripts/master/scriptler/{1}";

    public static final CatalogInfo CATALOG_INFO = new CatalogInfo("gh", REPO_BASE, REPO_BASE, DOWNLOAD_URL);

    @Override
    public List<ScriptInfo> getEntries() {
        return getEntries(ScriptInfo.COMPARATOR_BY_NAME);
    }

    @Override
    public String getDisplayName() {
        return "GitHub";
    }

    @Override
    public ScriptInfo getEntryById(String id) {
        for (ScriptInfo info : getEntries(null)) {
            if (id.equals(info.getId())) {
                return info;
            }
        }
        return null;
    }

    private List<ScriptInfo> getEntries(@CheckForNull Comparator<ScriptInfo> comparator){
        ScriptInfo[] scriptInfoArray = new ScriptInfo[0];
        try {
            scriptInfoArray = CentralScriptJsonCatalog.getCatalog().toList().list;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "not abe to load script infos from GH", e);
        }
        List<ScriptInfo> sortedScriptInfoList = Arrays.asList(scriptInfoArray);

        if(comparator != null)
            sortedScriptInfoList.sort(comparator);
    
        return sortedScriptInfoList;
    }
    
    @Override
    public CatalogInfo getInfo() {
        return CATALOG_INFO;
    }

    @Override
    public String getScriptSource(ScriptInfo scriptInfo) {

        final String scriptUrl = CATALOG_INFO.getReplacedDownloadUrl(scriptInfo.getName(), scriptInfo.getId());
        try (InputStream is = ProxyConfiguration.getInputStream(new URL(scriptUrl))) {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "not abe to load script sources from GH for: " + scriptInfo, e);
        }

        return null;
    }
}
