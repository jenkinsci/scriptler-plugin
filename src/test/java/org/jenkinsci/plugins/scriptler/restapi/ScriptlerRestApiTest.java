package org.jenkinsci.plugins.scriptler.restapi;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Functions;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.*;
import org.htmlunit.util.NameValuePair;
import org.jenkinsci.plugins.scriptler.ScriptlerManagementHelper;
import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.jenkinsci.plugins.scriptler.config.ScriptlerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ScriptlerRestApiTest {

    private static final String SCRIPT_ID = "dummy.groovy";

    private JenkinsRule j;

    @BeforeEach
    void setup(JenkinsRule j) throws IOException {
        this.j = j;

        ScriptlerManagementHelper.saveScript(SCRIPT_ID, "print \"hello $arg1, this is $arg2.\"", true);
        List<Parameter> parameters = List.of(new Parameter("arg1", "world"), new Parameter("arg2", "scriptler"));
        ScriptlerConfiguration.getConfiguration().getScriptById(SCRIPT_ID).setParameters(parameters);
    }

    @Test
    @Issue("SECURITY-691")
    void fixFolderTraversalThroughScriptId() throws Exception {
        String maliciousCode = "print 'hello'";

        // will be just aside scriptler.xml
        assertSaveFileFail("../clickOnMe", maliciousCode);

        assertSaveFileFail("/directlyInDiskRoot", maliciousCode);

        if (Functions.isWindows()) {
            assertSaveFileFail("//directlyInDiskRoot", maliciousCode);

            // C:\ + ...
            String rootLetter = Paths.get(".").toRealPath().getRoot().toString();
            assertSaveFileFail(rootLetter + "directlyInDiskRoot", maliciousCode);
        }
    }

    private void assertSaveFileFail(String scriptId, String scriptContent) {
        // will be just aside scriptler.xml
        IOException e = assertThrows(
                IOException.class, () -> ScriptlerManagementHelper.saveScript(scriptId, scriptContent, true));
        assertTrue(e.getMessage().contains("Invalid file path received"));
    }

    @Test
    void testSuccessWithDefaults() throws Exception {
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            HtmlPage runScriptPage = webClient.goTo("scriptler/runScript?id=dummy.groovy");
            HtmlForm form = runScriptPage.getFormByName("triggerscript");

            Page page = j.submit(form);

            j.assertGoodStatus(page);
            assertTrue(page.getWebResponse().getContentAsString().contains("hello world, this is scriptler."));
        }
    }

    @Test
    void testSuccessWithChangedScript() throws Exception {
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            HtmlPage runScriptPage = webClient.goTo("scriptler/runScript?id=dummy.groovy");
            HtmlForm triggerscript = runScriptPage.getFormByName("triggerscript");
            HtmlTextArea script = runScriptPage.getElementByName("script");
            script.setText("print \"welcome, $arg1 and $arg2!\"");

            HtmlPage page = j.submit(triggerscript);

            j.assertGoodStatus(page);
            assertTrue(page.getWebResponse().getContentAsString().contains("welcome, world and scriptler!"));
        }
    }

    @Test
    void testUnknownScript() {
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            assertThrows(
                    FailingHttpStatusCodeException.class,
                    () -> webClient.goTo("scriptler/runScript?id=unknown.groovy"));
        }
    }

    @Test
    @Issue("SECURITY-3205")
    void fixFolderTraversalThroughDeleteScript() throws Exception {
        Path configurationFile = ScriptlerConfiguration.getXmlFile().getFile().toPath();
        String path = "../" + configurationFile.getFileName();

        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            URL rootUrl = new URL(webClient.getContextPath() + "scriptler/removeScript");
            WebRequest req = new WebRequest(rootUrl, HttpMethod.POST);
            req.setRequestParameters(Collections.singletonList(new NameValuePair("id", path)));
            webClient.addCrumb(req);
            FailingHttpStatusCodeException e =
                    assertThrows(FailingHttpStatusCodeException.class, () -> webClient.getPage(req));
            if (e.getStatusCode() != 400) {
                // some other kind of error that we're not checking for
                throw e;
            }
            assertTrue(Files.exists(configurationFile), "The configuration file was deleted");
            assertTrue(e.getResponse().getContentAsString().contains("Invalid file path received: " + path));
        }
    }
}
