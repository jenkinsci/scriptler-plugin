
package org.jenkinsci.plugins.scriptler.restapi;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URL;
import java.util.Collections;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.html.*;
//import org.htmlunit.javascript.host.URL;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.model.FileParameterValue.FileItemImpl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.fileupload.FileItem;
import org.htmlunit.util.NameValuePair;
import org.jenkinsci.plugins.scriptler.ScriptlerManagementHelper;
import org.jenkinsci.plugins.scriptler.ScriptlerManagement;
import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.jenkinsci.plugins.scriptler.config.ScriptlerConfiguration;
import org.junit.*;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.Page;

public class ScriptlerRestApiTest {

    private static final String SCRIPT_ID = "dummy.groovy";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @ClassRule
    public static BuildWatcher bw = new BuildWatcher();

    @Before
    public void setup() throws Exception {
        final ScriptlerManagement scriptler = ExtensionList.lookupSingleton(ScriptlerManagement.class);
        ScriptlerManagementHelper helper = new ScriptlerManagementHelper(scriptler);
        saveFile(helper, SCRIPT_ID, "print \"hello $arg1, this is $arg2.\"");

        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("arg1", "world"));
        parameters.add(new Parameter("arg2", "scriptler"));
        scriptler.getConfiguration().getScriptById(SCRIPT_ID).setParameters(parameters);
    }
    
    private void saveFile(ScriptlerManagementHelper helper, String scriptId, String scriptContent) throws Exception {
        Path f = Files.createTempFile("script", "-temp");
        Files.writeString(f, scriptContent);
        FileItem fi = new FileItemImpl(f.toFile());
        helper.saveScript(fi, true, scriptId);
    }
    
    @Test
    @Issue("SECURITY-691")
    public void fixFolderTraversalThroughScriptId() throws Exception{
        ScriptlerManagement scriptler = ExtensionList.lookupSingleton(ScriptlerManagement.class);
        ScriptlerManagementHelper helper = new ScriptlerManagementHelper(scriptler);
        
        String maliciousCode = "print 'hello'";
    
        // will be just aside scriptler.xml
        assertSaveFileFail(helper, "../clickOnMe", maliciousCode);

        if(Functions.isWindows()){
            // will be used as relative inside the folder
            saveFile(helper, "/directlyInDiskRoot", maliciousCode);
            
            assertSaveFileFail(helper, "//directlyInDiskRoot", maliciousCode);
    
            // C:\ + ...
            String rootLetter = new File(".").getAbsolutePath().substring(0, 3);
            assertSaveFileFail(helper, rootLetter + "directlyInDiskRoot", maliciousCode);
        }else{
            assertSaveFileFail(helper, "/directlyInDiskRoot", maliciousCode);
        }
    }
    
    private void assertSaveFileFail(ScriptlerManagementHelper helper, String scriptId, String scriptContent) throws Exception {
        try{
            // will be just aside scriptler.xml
            saveFile(helper, scriptId, scriptContent);
            fail();
        }
        catch(IOException e){
            assertTrue(e.getMessage().contains("Invalid file path received"));
        }
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

    @Test
    @Issue("SECURITY-3205")
    public void fixFolderTraversalThroughDeleteScript() throws Exception {
        File configurationFile = ScriptlerConfiguration.getXmlFile().getFile();
        String path = "../" + configurationFile.getName();

        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            URL rootUrl = new URL(webClient.getContextPath() + "scriptler/removeScript");
            WebRequest req = new WebRequest(rootUrl, HttpMethod.POST);
            req.setRequestParameters(Collections.singletonList(new NameValuePair("id", path)));
            webClient.addCrumb(req);
            webClient.getPage(req);
            fail();
        } catch (FailingHttpStatusCodeException e) {
            if (e.getStatusCode() != 400) {
                // some other kind of error that we're not checking for
                throw e;
            }
            if (!configurationFile.exists()) {
                fail("The configuration file was deleted");
            }
            assert(e.getResponse().getContentAsString().contains("Invalid file path received: " + path));
        }
    }
}
