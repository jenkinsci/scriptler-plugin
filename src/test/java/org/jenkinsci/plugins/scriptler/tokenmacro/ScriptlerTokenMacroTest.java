package org.jenkinsci.plugins.scriptler.tokenmacro;

import hudson.model.FreeStyleBuild;
import hudson.model.FileParameterValue.FileItemImpl;
import hudson.model.FreeStyleProject;
import hudson.util.StreamTaskListener;

import java.io.File;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.scriptler.ScriptlerManagementHelper;
import org.jenkinsci.plugins.scriptler.ScriptlerManagment;
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

        final ScriptlerManagment scriptler = j.getInstance().getExtensionList(ScriptlerManagment.class).get(0);
        ScriptlerManagementHelper helper = new ScriptlerManagementHelper(scriptler);
        File f = new File("dummy.groovy");
        FileUtils.writeStringToFile(f, "return 'hello world'");
        FileItem fi = new FileItemImpl(f);
        helper.saveScript(fi, true, "dummy.groovy");

        FreeStyleProject p = j.createFreeStyleProject("foo");
        FreeStyleBuild b = p.scheduleBuild2(0).get();

        final StreamTaskListener listener = new StreamTaskListener(System.out);

        Assert.assertEquals("hello world", TokenMacro.expand(b, listener, "${SCRIPTLER,scriptId=\"dummy.groovy\"}"));

    }
}
