package org.jenkinsci.plugins.scriptler;

import org.apache.commons.fileupload.FileItem;

public class ScriptlerManagementHelper {

    private final ScriptlerManagment scriptler;

    public ScriptlerManagementHelper(ScriptlerManagment scriptler) {
        this.scriptler = scriptler;
    }

    public void saveScript(FileItem file, boolean nonAdministerUsing, String fileName) throws Exception {
        scriptler.saveScript(file, nonAdministerUsing, fileName);
    }
}
