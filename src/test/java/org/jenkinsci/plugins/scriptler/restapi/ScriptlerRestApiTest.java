package org.jenkinsci.plugins.scriptler.restapi;

import static org.junit.Assert.assertEquals;
import hudson.model.FileParameterValue.FileItemImpl;

import java.io.File;
import java.net.URLEncoder;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.scriptler.ScriptlerManagementHelper;
import org.jenkinsci.plugins.scriptler.ScriptlerManagement;
import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;

public class ScriptlerRestApiTest {

    private static final String SCRIPT_ID = "dummy.groovy";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setup() throws Exception {
        final ScriptlerManagement scriptler = j.getInstance().getExtensionList(ScriptlerManagement.class).get(0);
        ScriptlerManagementHelper helper = new ScriptlerManagementHelper(scriptler);
        File f = new File(SCRIPT_ID);
        FileUtils.writeStringToFile(f, "print \"hello $arg1, this is $arg2.\"");
        FileItem fi = new FileItemImpl(f);
        helper.saveScript(fi, true, SCRIPT_ID);

        scriptler.getConfiguration().getScriptById(SCRIPT_ID)
                .setParameters(new Parameter[]{ new Parameter("arg1", "world"), new Parameter("arg2", "scriptler") });
    }

    @Test
    public void testSuccessWithDefaults() throws Exception {
        Page goTo = j.createWebClient().goTo("scriptler/run/dummy.groovy", "text/plain");
        j.assertGoodStatus(goTo);

        assertEquals("hello world, this is scriptler.", goTo.getWebResponse().getContentAsString());
    }

    @Test
    public void testSuccessWithAllChanged() throws Exception {
        Page goTo = j.createWebClient()
                .goTo("scriptler/run/dummy.groovy?script=" + URLEncoder.encode("print \"welcome, $arg1 and $arg2!\"",
                        "UTF-8") + "&arg1=foo&arg2=bar&contentType=application/foobar", "application/foobar");
        j.assertGoodStatus(goTo);

        assertEquals("welcome, foo and bar!", goTo.getWebResponse().getContentAsString());
    }

    @Test(expected = FailingHttpStatusCodeException.class)
    public void testUnknownScript() throws Exception {
        j.createWebClient().goTo("scriptler/run/unknown.groovy", "text/plain");
    }
}