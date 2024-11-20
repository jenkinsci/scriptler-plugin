package org.jenkinsci.plugins.scriptler.share.gh;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.DownloadService.Downloadable;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JsonConfig;
import org.jenkinsci.plugins.scriptler.share.ScriptInfo;

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

    @SuppressWarnings("unchecked")
    public Collection<ScriptInfo> getScripts() throws IOException {
        JSONObject d = getData();
        if (d == null) {
            return List.of();
        }
        JsonConfig config = new JsonConfig();
        config.setCollectionType(List.class);
        config.setRootClass(ScriptInfo.class);
        return JSONArray.toCollection(d.getJSONArray("list"), config);
    }

    public static CentralScriptJsonCatalog getCatalog() {
        return ExtensionList.lookupSingleton(CentralScriptJsonCatalog.class);
    }
}
