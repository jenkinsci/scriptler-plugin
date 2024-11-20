package org.jenkinsci.plugins.scriptler;

import hudson.ExtensionList;
import hudson.model.FileParameterValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.fileupload2.core.FileItem;

public final class ScriptlerManagementHelper {

    private ScriptlerManagementHelper() {}

    public static void saveScript(String scriptId, String contents, boolean nonAdministerUsing) throws IOException {
        final ScriptlerManagement scriptler = ExtensionList.lookupSingleton(ScriptlerManagement.class);

        Path f = Files.createTempFile("script", "-temp.groovy");
        Files.writeString(f, contents, StandardCharsets.UTF_8);
        FileItem<?> fi = new FileParameterValue.FileItemImpl2(f.toFile());
        scriptler.saveScript(fi, nonAdministerUsing, scriptId);
        Files.delete(f);
    }
}
