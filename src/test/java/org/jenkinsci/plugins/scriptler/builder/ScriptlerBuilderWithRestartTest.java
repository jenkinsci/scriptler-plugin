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

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import hudson.ExtensionList;
import hudson.model.FileParameterValue;
import hudson.model.FreeStyleProject;
import net.sf.json.JSONObject;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.scriptler.ScriptlerManagement;
import org.jenkinsci.plugins.scriptler.ScriptlerManagementHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Warning: a user without RUN_SCRIPT can currently only clone an existing builder INSIDE a project.
 * You can search CLONE_NOTE inside this test to see the cases
 */
public class ScriptlerBuilderWithRestartTest {
    @Rule
    public RestartableJenkinsRule r = new RestartableJenkinsRule();

    private static final String SCRIPT_USABLE_1 = "script_usable_1.groovy";
    private static final String SCRIPT_USABLE_2 = "script_usable_2.groovy";
    private static final String SCRIPT_USABLE_3 = "script_usable_3.groovy";
    private static final String SCRIPT_USABLE_4 = "script_usable_4.groovy";

    private static final String SCRIPT_NOT_USABLE = "not_usable.groovy";

    @Test
    public void configRoundtrip() throws Exception {
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                r.j.jenkins.setCrumbIssuer(null);

                ScriptlerManagement scriptler = ExtensionList.lookupSingleton(ScriptlerManagement.class);
                ScriptlerManagementHelper helper = new ScriptlerManagementHelper(scriptler);

                setupScript(helper, SCRIPT_USABLE_1, true);
                setupScript(helper, SCRIPT_USABLE_2, true);
                setupScript(helper, SCRIPT_USABLE_3, true);
                setupScript(helper, SCRIPT_USABLE_4, true);
                setupScript(helper, SCRIPT_NOT_USABLE, false);

                FreeStyleProject project = r.j.createFreeStyleProject("test");

                JenkinsRule.WebClient wc = r.j.createWebClient();

                WebRequest request = new WebRequest(new URL(r.j.getURL() + project.getShortUrl() + "configSubmit"), HttpMethod.POST);

                final String projectName = project.getName();
                request.setRequestParameters(Arrays.asList(new NameValuePair("json", JSONObject.fromObject(
                        new HashMap<String, Object>() {{
                            put("name", projectName);
                            put("builder", new HashMap<String, Object>() {{
                                put("kind", ScriptlerBuilder.class.getName());
                                put("builderId", "");
                                put("scriptlerScriptId", SCRIPT_USABLE_1);
                                put("propagateParams", true);
                                put("defineParams", new HashMap<String, List<Map<String, String>>>() {{
                                    put("parameters", Arrays.asList(
                                            new HashMap<String, String>() {{
                                                put("name", "param1");
                                                put("value", "value1");
                                            }},
                                            new HashMap<String, String>() {{
                                                put("name", "param2");
                                                put("value", "value2");
                                            }}
                                    ));
                                }});
                            }});
                        }}
                ).toString())));
                HtmlPage page = wc.getPage(request);
                r.j.assertGoodStatus(page);

                ScriptlerBuilder scriptlerBuilder = project.getBuildersList().get(ScriptlerBuilder.class);
                assertNotNull(scriptlerBuilder);
                assertNotNull(scriptlerBuilder.getBuilderId());
                assertEquals(SCRIPT_USABLE_1, scriptlerBuilder.getScriptId());
                assertEquals(true, scriptlerBuilder.isPropagateParams());
                assertEquals(2, scriptlerBuilder.getParameters().length);
                assertEquals("param1", scriptlerBuilder.getParameters()[0].getName());
                assertEquals("value1", scriptlerBuilder.getParameters()[0].getValue());
                assertEquals("param2", scriptlerBuilder.getParameters()[1].getName());
                assertEquals("value2", scriptlerBuilder.getParameters()[1].getValue());
            }
        });

        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                FreeStyleProject p = r.j.jenkins.getItemByFullName("test", FreeStyleProject.class);

                ScriptlerBuilder scriptlerBuilder = p.getBuildersList().get(ScriptlerBuilder.class);
                assertNotNull(scriptlerBuilder);
                assertNotNull(scriptlerBuilder.getBuilderId());
                assertEquals(SCRIPT_USABLE_1, scriptlerBuilder.getScriptId());
                assertEquals(true, scriptlerBuilder.isPropagateParams());
                assertEquals(2, scriptlerBuilder.getParameters().length);
                assertEquals("param1", scriptlerBuilder.getParameters()[0].getName());
                assertEquals("value1", scriptlerBuilder.getParameters()[0].getValue());
                assertEquals("param2", scriptlerBuilder.getParameters()[1].getName());
                assertEquals("value2", scriptlerBuilder.getParameters()[1].getValue());
            }
        });
    }

    private void setupScript(ScriptlerManagementHelper helper, String scriptId, boolean nonAdministerUsing) throws Exception {
        File f = new File(scriptId);
        FileUtils.writeStringToFile(f, "print 'Hello World!'");
        FileItem fi = new FileParameterValue.FileItemImpl(f);
        helper.saveScript(fi, nonAdministerUsing, scriptId);
        f.delete();
    }

    @Test
    public void configRoundtripConfigXml() throws Exception {
        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                r.j.jenkins.setCrumbIssuer(null);

                ScriptlerManagement scriptler = ExtensionList.lookupSingleton(ScriptlerManagement.class);
                ScriptlerManagementHelper helper = new ScriptlerManagementHelper(scriptler);

                setupScript(helper, SCRIPT_USABLE_1, true);
                setupScript(helper, SCRIPT_USABLE_2, true);
                setupScript(helper, SCRIPT_USABLE_3, true);
                setupScript(helper, SCRIPT_USABLE_4, true);
                setupScript(helper, SCRIPT_NOT_USABLE, false);

                FreeStyleProject project = r.j.createFreeStyleProject("test");

                JenkinsRule.WebClient wc = r.j.createWebClient();

                XmlPage xmlPage = wc.goToXml(project.getShortUrl() + "config.xml");
                r.j.assertGoodStatus(xmlPage);
                String xml = xmlPage.getWebResponse().getContentAsString();

                String modifiedXml = xml.replace("<builders/>", "" +
                        "<builders>\n" +
                        "  <org.jenkinsci.plugins.scriptler.builder.ScriptlerBuilder>\n" +
                        "    <builderId></builderId>\n" +
                        "    <scriptId>" + SCRIPT_USABLE_1 + "</scriptId>\n" +
                        "    <propagateParams>true</propagateParams>\n" +
                        "    <parameters>\n" +
                        "      <org.jenkinsci.plugins.scriptler.config.Parameter>\n" +
                        "        <name>param1</name>\n" +
                        "        <value>value1</value>\n" +
                        "      </org.jenkinsci.plugins.scriptler.config.Parameter>\n" +
                        "      <org.jenkinsci.plugins.scriptler.config.Parameter>\n" +
                        "        <name>param2</name>\n" +
                        "        <value>value2</value>\n" +
                        "      </org.jenkinsci.plugins.scriptler.config.Parameter>\n" +
                        "    </parameters>\n" +
                        "  </org.jenkinsci.plugins.scriptler.builder.ScriptlerBuilder>\n" +
                        "</builders>"
                );

                WebRequest request = new WebRequest(new URL(project.getAbsoluteUrl() + "config.xml"), HttpMethod.POST);
                request.setRequestBody(modifiedXml);
                request.setEncodingType(null);
                HtmlPage page = wc.getPage(request);
                r.j.assertGoodStatus(page);

                project = r.j.jenkins.getItemByFullName(project.getFullName(), FreeStyleProject.class);
                ScriptlerBuilder scriptlerBuilder = project.getBuildersList().get(ScriptlerBuilder.class);
                assertNotNull(scriptlerBuilder);
                assertTrue(scriptlerBuilder.getBuilderId().equals(""));
                assertEquals(SCRIPT_USABLE_1, scriptlerBuilder.getScriptId());
                assertEquals(true, scriptlerBuilder.isPropagateParams());
                assertEquals(2, scriptlerBuilder.getParameters().length);
                assertEquals("param1", scriptlerBuilder.getParameters()[0].getName());
                assertEquals("value1", scriptlerBuilder.getParameters()[0].getValue());
                assertEquals("param2", scriptlerBuilder.getParameters()[1].getName());
                assertEquals("value2", scriptlerBuilder.getParameters()[1].getValue());
            }
        });

        r.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                FreeStyleProject p = r.j.jenkins.getItemByFullName("test", FreeStyleProject.class);

                ScriptlerBuilder scriptlerBuilder = p.getBuildersList().get(ScriptlerBuilder.class);
                assertNotNull(scriptlerBuilder);
                assertNotNull(scriptlerBuilder.getBuilderId());
                assertEquals(SCRIPT_USABLE_1, scriptlerBuilder.getScriptId());
                assertEquals(true, scriptlerBuilder.isPropagateParams());
                assertEquals(2, scriptlerBuilder.getParameters().length);
                assertEquals("param1", scriptlerBuilder.getParameters()[0].getName());
                assertEquals("value1", scriptlerBuilder.getParameters()[0].getValue());
                assertEquals("param2", scriptlerBuilder.getParameters()[1].getName());
                assertEquals("value2", scriptlerBuilder.getParameters()[1].getValue());
            }
        });
    }
}
