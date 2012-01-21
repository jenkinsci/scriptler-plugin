package org.jenkinsci.plugins.scriptler.share.gh;

import hudson.Extension;
import hudson.ProxyConfiguration;
import hudson.Util;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jenkinsci.plugins.scriptler.share.ScriptInfo;
import org.jenkinsci.plugins.scriptler.share.ScriptInfoCatalog;
import org.jvnet.hudson.plugins.scriptler.share.CatalogInfo;

/**
 * Provides access to the scriptler scripts shared at https://github.com/jenkinsci/jenkins-scripts
 * 
 * @author Dominik Bartholdi (imod)
 * 
 */
@Extension
public class GHCatalog extends ScriptInfoCatalog<ScriptInfo> {

    public static final String REPO_BASE = "https://github.com/jenkinsci/jenkins-scripts/tree/master/scriptler";
    public static final String DOWNLOAD_URL = "https://raw.github.com/jenkinsci/jenkins-scripts/master/scriptler/{1}";

    public static final CatalogInfo CATALOG_INFO = new CatalogInfo("gh", null, REPO_BASE, DOWNLOAD_URL);

    public List<ScriptInfo> getEntries() {
        try {
            return Arrays.asList(CentralScriptJsonCatalog.all().get(CentralScriptJsonCatalog.class).toList().list);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    public ScriptInfo getEntryById(String id) {
        for (ScriptInfo info : getEntries()) {
            if (id.equals(info.script)) {
                return info;
            }
        }
        return null;
    }

    public CatalogInfo getInfo() {
        return CATALOG_INFO;
    }

    @Override
    public String getScriptSource(ScriptInfo scriptInfo) {

        try {

            final String scriptUrl = CATALOG_INFO.getReplacedDownloadUrl(scriptInfo.getName(), scriptInfo.getId());
            System.out.println("-->" + scriptUrl);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Util.copyStreamAndClose(ProxyConfiguration.open(new URL(scriptUrl)).getInputStream(), out);
            return out.toString("UTF-8");

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
