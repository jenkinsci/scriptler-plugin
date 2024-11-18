package org.jenkinsci.plugins.scriptler.tokenmacro;

import hudson.ExtensionList;
import hudson.model.FileParameterValue.FileItemImpl;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.StreamTaskListener;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.fileupload.FileItem;
import org.jenkinsci.plugins.scriptler.ScriptlerManagement;
import org.jenkinsci.plugins.scriptler.ScriptlerManagementHelper;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ScriptlerTokenMacroTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testExecutesScript() throws Exception {

        final ScriptlerManagement scriptler = ExtensionList.lookupSingleton(ScriptlerManagement.class);
        ScriptlerManagementHelper helper = new ScriptlerManagementHelper(scriptler);
        Path f = Paths.get("dummy.groovy");
        Files.writeString(f, "return \"hello world ${build.number}\"", StandardCharsets.UTF_8);
        FileItem fi = new FileItemImpl(f.toFile());
        helper.saveScript(fi, true, "dummy.groovy");

        FreeStyleProject p = j.createFreeStyleProject("foo");
        FreeStyleBuild b = p.scheduleBuild2(0).get();

        final StreamTaskListener listener = StreamTaskListener.fromStdout();

        Assert.assertEquals("hello world 1", TokenMacro.expand(b, listener, "${SCRIPTLER,scriptId=\"dummy.groovy\"}"));
    }
}
