/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.scriptler.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.htmlunit.xml.XmlPage;
import org.jenkinsci.plugins.scriptler.ScriptlerManagementHelper;
import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Warning: a user without RUN_SCRIPT can currently only clone an existing builder INSIDE a project.
 * You can search CLONE_NOTE inside this test to see the cases
 */
@WithJenkins
class ScriptlerBuilderWithRestartTest {
    private static final String SCRIPT_CONTENTS = "print 'Hello World!'";
    private static final String SCRIPT_USABLE_1 = "script_usable_1.groovy";
    private static final String SCRIPT_USABLE_2 = "script_usable_2.groovy";
    private static final String SCRIPT_USABLE_3 = "script_usable_3.groovy";
    private static final String SCRIPT_USABLE_4 = "script_usable_4.groovy";

    private static final String SCRIPT_NOT_USABLE = "not_usable.groovy";

    @Test
    void configRoundTrip(JenkinsRule r) throws Throwable {
        r.jenkins.setCrumbIssuer(null);

        ScriptlerManagementHelper.saveScript(SCRIPT_USABLE_1, SCRIPT_CONTENTS, true);
        ScriptlerManagementHelper.saveScript(SCRIPT_USABLE_2, SCRIPT_CONTENTS, true);
        ScriptlerManagementHelper.saveScript(SCRIPT_USABLE_3, SCRIPT_CONTENTS, true);
        ScriptlerManagementHelper.saveScript(SCRIPT_USABLE_4, SCRIPT_CONTENTS, true);
        ScriptlerManagementHelper.saveScript(SCRIPT_NOT_USABLE, SCRIPT_CONTENTS, false);

        FreeStyleProject project = r.createFreeStyleProject("test");

        try (JenkinsRule.WebClient wc = r.createWebClient()) {

            WebRequest request =
                    new WebRequest(new URL(r.getURL() + project.getShortUrl() + "configSubmit"), HttpMethod.POST);

            final String projectName = project.getName();
            request.setRequestParameters(List.of(new NameValuePair(
                    "json",
                    JSONObject.fromObject(Map.of(
                                    "name",
                                    projectName,
                                    "builder",
                                    Map.of(
                                            "kind",
                                            ScriptlerBuilder.class.getName(),
                                            "builderId",
                                            "",
                                            "scriptlerScriptId",
                                            SCRIPT_USABLE_1,
                                            "propagateParams",
                                            true,
                                            "defineParams",
                                            Map.of(
                                                    "parameters",
                                                    List.of(
                                                            Map.of("name", "param1", "value", "value1"),
                                                            Map.of("name", "param2", "value", "value2"))))))
                            .toString())));
            HtmlPage page = wc.getPage(request);
            r.assertGoodStatus(page);

            ScriptlerBuilder scriptlerBuilder = project.getBuildersList().get(ScriptlerBuilder.class);
            assertNotNull(scriptlerBuilder);
            assertNotNull(scriptlerBuilder.getBuilderId());
            assertEquals(SCRIPT_USABLE_1, scriptlerBuilder.getScriptId());
            assertTrue(scriptlerBuilder.isPropagateParams());
            assertIterableEquals(
                    List.of(new Parameter("param1", "value1"), new Parameter("param2", "value2")),
                    scriptlerBuilder.getParametersList());
        }

        r.restart();

        FreeStyleProject p = r.jenkins.getItemByFullName("test", FreeStyleProject.class);

        ScriptlerBuilder scriptlerBuilder =
                Objects.requireNonNull(p).getBuildersList().get(ScriptlerBuilder.class);
        assertNotNull(scriptlerBuilder);
        assertNotNull(scriptlerBuilder.getBuilderId());
        assertEquals(SCRIPT_USABLE_1, scriptlerBuilder.getScriptId());
        assertTrue(scriptlerBuilder.isPropagateParams());
        assertIterableEquals(
                List.of(new Parameter("param1", "value1"), new Parameter("param2", "value2")),
                scriptlerBuilder.getParametersList());
    }

    @Test
    void configRoundTripConfigXml(JenkinsRule r) throws Throwable {
        r.jenkins.setCrumbIssuer(null);

        ScriptlerManagementHelper.saveScript(SCRIPT_USABLE_1, SCRIPT_CONTENTS, true);
        ScriptlerManagementHelper.saveScript(SCRIPT_USABLE_2, SCRIPT_CONTENTS, true);
        ScriptlerManagementHelper.saveScript(SCRIPT_USABLE_3, SCRIPT_CONTENTS, true);
        ScriptlerManagementHelper.saveScript(SCRIPT_USABLE_4, SCRIPT_CONTENTS, true);
        ScriptlerManagementHelper.saveScript(SCRIPT_NOT_USABLE, SCRIPT_CONTENTS, false);

        FreeStyleProject project = r.createFreeStyleProject("test");

        try (JenkinsRule.WebClient wc = r.createWebClient()) {

            XmlPage xmlPage = wc.goToXml(project.getShortUrl() + "config.xml");
            r.assertGoodStatus(xmlPage);
            String xml = xmlPage.getWebResponse().getContentAsString();

            String modifiedXml = xml.replace(
                    "<builders/>",
                    String.format(
                            """
                                <builders>
                                  <org.jenkinsci.plugins.scriptler.builder.ScriptlerBuilder>
                                    <builderId></builderId>
                                    <scriptId>%1s</scriptId>
                                    <propagateParams>true</propagateParams>
                                    <parameters>
                                      <org.jenkinsci.plugins.scriptler.config.Parameter>
                                        <name>param1</name>
                                        <value>value1</value>
                                      </org.jenkinsci.plugins.scriptler.config.Parameter>
                                      <org.jenkinsci.plugins.scriptler.config.Parameter>
                                        <name>param2</name>
                                        <value>value2</value>
                                      </org.jenkinsci.plugins.scriptler.config.Parameter>
                                    </parameters>
                                  </org.jenkinsci.plugins.scriptler.builder.ScriptlerBuilder>
                                </builders>""",
                            SCRIPT_USABLE_1));

            WebRequest request = new WebRequest(new URL(project.getAbsoluteUrl() + "config.xml"), HttpMethod.POST);
            request.setRequestBody(modifiedXml);
            request.setEncodingType(null);
            HtmlPage page = wc.getPage(request);
            r.assertGoodStatus(page);

            project = r.jenkins.getItemByFullName(project.getFullName(), FreeStyleProject.class);
            ScriptlerBuilder scriptlerBuilder =
                    Objects.requireNonNull(project).getBuildersList().get(ScriptlerBuilder.class);
            assertNotNull(scriptlerBuilder);
            assertEquals("", scriptlerBuilder.getBuilderId());
            assertEquals(SCRIPT_USABLE_1, scriptlerBuilder.getScriptId());
            assertTrue(scriptlerBuilder.isPropagateParams());
            assertIterableEquals(
                    List.of(new Parameter("param1", "value1"), new Parameter("param2", "value2")),
                    scriptlerBuilder.getParametersList());
        }

        r.restart();

        FreeStyleProject p = r.jenkins.getItemByFullName("test", FreeStyleProject.class);

        ScriptlerBuilder scriptlerBuilder =
                Objects.requireNonNull(p).getBuildersList().get(ScriptlerBuilder.class);
        assertNotNull(scriptlerBuilder);
        assertNotNull(scriptlerBuilder.getBuilderId());
        assertEquals(SCRIPT_USABLE_1, scriptlerBuilder.getScriptId());
        assertTrue(scriptlerBuilder.isPropagateParams());
        assertIterableEquals(
                List.of(new Parameter("param1", "value1"), new Parameter("param2", "value2")),
                scriptlerBuilder.getParametersList());
    }
}
