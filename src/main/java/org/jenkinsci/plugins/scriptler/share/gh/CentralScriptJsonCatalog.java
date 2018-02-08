package org.jenkinsci.plugins.scriptler.share.gh;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.DownloadService.Downloadable;

import java.io.IOException;

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.scriptler.share.ScriptInfoList;

/**
 * Gets the GitHub catalog from <a href="http://mirrors.jenkins-ci.org/updates/updates/org.jenkinsci.plugins.scriptler.CentralScriptJsonCatalog.json">jenkins-ci/org.jenkinsci.plugins.scriptler.CentralScriptJsonCatalog.json</a>. This catalog is updated by a background crawler on the
 * Jenkins infrastructure site.
 * 
 * @author Dominik Bartholdi (imod)
 */
@Extension
public class CentralScriptJsonCatalog extends Downloadable {

    public static final String ID = "org.jenkinsci.plugins.scriptler.CentralScriptJsonCatalog";

    public CentralScriptJsonCatalog() {
        super(ID);
    }

    public ScriptInfoList toList() throws IOException {
        JSONObject d = getData();
        if (d == null) {
            return new ScriptInfoList();
        }
        return (ScriptInfoList) JSONObject.toBean(d, ScriptInfoList.class);
    }

    public static CentralScriptJsonCatalog getCatalog() {
        return ExtensionList.lookup(CentralScriptJsonCatalog.class).get(CentralScriptJsonCatalog.class);
    }
}
