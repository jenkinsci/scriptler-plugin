package org.jenkinsci.plugins.scriptler.restapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

//import com.gargoylesoftware.htmlunit.HttpMethod;
//import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.*;
//import com.gargoylesoftware.htmlunit.javascript.host.URL;
import hudson.model.FileParameterValue.FileItemImpl;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.scriptler.ScriptlerManagementHelper;
import org.jenkinsci.plugins.scriptler.ScriptlerManagement;
import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.junit.*;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;

public class ScriptlerRestApiTest {

    private static final String SCRIPT_ID = "dummy.groovy";

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Rule
    public BuildWatcher bw = new BuildWatcher();

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

        JenkinsRule.WebClient webClient = j.createWebClient();
        HtmlPage runScriptPage = webClient.goTo("scriptler/runScript?id=dummy.groovy");
        HtmlForm form = runScriptPage.getFormByName("triggerscript");

        Page page = j.submit(form);

        j.assertGoodStatus(page);
        assertTrue(page.getWebResponse().getContentAsString().contains("hello world, this is scriptler."));
    }

    @Test
    @Ignore("no idea why this does not work, htmlunit does not send the modified textarea...")
    public void testSuccessWithChangedScript() throws Exception {

        JenkinsRule.WebClient webClient = j.createWebClient();
        HtmlPage runScriptPage = webClient.goTo("scriptler/runScript?id=dummy.groovy");
        HtmlForm triggerscript = runScriptPage.getFormByName("triggerscript");
        HtmlTextArea script = (HtmlTextArea)runScriptPage.getElementByName("script");
        script.setText("print \"welcome, $arg1 and $arg2!\"");

        HtmlPage page = j.submit(triggerscript);

        j.assertGoodStatus(page);
        assertTrue(page.getWebResponse().getContentAsString().contains("welcome, world and scriptler!"));
    }

    @Test(expected = FailingHttpStatusCodeException.class)
    public void testUnknownScript() throws Exception {
        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.goTo("scriptler/runScript?id=unknown.groovy");
    }
}