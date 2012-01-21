package org.jenkinsci.plugins.scriptler.share.gh;

import hudson.Extension;
import hudson.model.DownloadService.Downloadable;

import java.io.IOException;

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.scriptler.share.DefaultScriptInfoList;

@Extension
public class CentralScriptJsonCatalog extends Downloadable {

    public static final String ID = "org.jenkins-ci.plugins.scriptler.CentralScriptJsonCatalog";

    public CentralScriptJsonCatalog() {
        super(ID);
    }

    public DefaultScriptInfoList toList() throws IOException {
        JSONObject d = getData();
        if (d == null) {
            return new DefaultScriptInfoList();
        }
        return (DefaultScriptInfoList) JSONObject.toBean(d, DefaultScriptInfoList.class);
    }

}
