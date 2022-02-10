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

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import hudson.ExtensionList;
import hudson.model.FileParameterValue;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Project;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.scriptler.ScriptlerManagement;
import org.jenkinsci.plugins.scriptler.ScriptlerManagementHelper;
import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;

/**
 * Warning: a user without RUN_SCRIPT can currently only clone an existing builder INSIDE a project.
 * You can search CLONE_NOTE inside this test to see the cases
 */
public class ScriptlerBuilderTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static final String SCRIPT_USABLE_1 = "script_usable_1.groovy";
    private static final String SCRIPT_USABLE_2 = "script_usable_2.groovy";
    private static final String SCRIPT_USABLE_3 = "script_usable_3.groovy";
    private static final String SCRIPT_USABLE_4 = "script_usable_4.groovy";

    private static final String SCRIPT_NOT_USABLE = "not_usable.groovy";

    @Before
    public void setupScripts() throws Exception {
        j.jenkins.setCrumbIssuer(null);

        ScriptlerManagement scriptler = ExtensionList.lookupSingleton(ScriptlerManagement.class);
        ScriptlerManagementHelper helper = new ScriptlerManagementHelper(scriptler);

        setupScript(helper, SCRIPT_USABLE_1, true);
        setupScript(helper, SCRIPT_USABLE_2, true);
        setupScript(helper, SCRIPT_USABLE_3, true);
        setupScript(helper, SCRIPT_USABLE_4, true);
        setupScript(helper, SCRIPT_NOT_USABLE, false);
    }

    private void setupScript(ScriptlerManagementHelper helper, String scriptId, boolean nonAdministerUsing) throws Exception {
        File f = new File(scriptId);
        FileUtils.writeStringToFile(f, "print 'Hello World!'");
        FileItem fi = new FileParameterValue.FileItemImpl(f);
        helper.saveScript(fi, nonAdministerUsing, scriptId);
        f.delete();
    }

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(ScriptlerBuilder.class).usingGetClass().verify();
    }

    @Test
    public void configRoundtripWebUI() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("test");

        JenkinsRule.WebClient wc = j.createWebClient();

        WebRequest request = new WebRequest(new URL(j.getURL() + project.getShortUrl() + "configSubmit"), HttpMethod.POST);

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
        j.assertGoodStatus(page);

        ScriptlerBuilder scriptlerBuilder = refreshProject(project).getBuildersList().get(ScriptlerBuilder.class);
        assertNotNull(scriptlerBuilder);
        assertNotNull(scriptlerBuilder.getBuilderId());
        assertEquals(SCRIPT_USABLE_1, scriptlerBuilder.getScriptId());
        assertEquals(true, scriptlerBuilder.isPropagateParams());
        assertThat(scriptlerBuilder.getParametersList(), hasSize(2));
        assertThat(scriptlerBuilder.getParametersList(), hasItems(new Parameter("param1", "value1"), new Parameter("param2", "value2")));
    }

    @Test
    public void configRoundtripConfigXml() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("test");

        JenkinsRule.WebClient wc = j.createWebClient();

        WebRequest request = new WebRequest(new URL(j.getURL() + project.getShortUrl() + "configSubmit"), HttpMethod.POST);

        String xml = retrieveXmlConfigForProject(wc, project);
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

        HtmlPage page = postXmlConfigForProject(wc, project, modifiedXml);
        j.assertGoodStatus(page);

        ScriptlerBuilder scriptlerBuilder = refreshProject(project).getBuildersList().get(ScriptlerBuilder.class);
        assertNotNull(scriptlerBuilder);
        assertTrue(scriptlerBuilder.getBuilderId().equals(""));
        assertEquals(SCRIPT_USABLE_1, scriptlerBuilder.getScriptId());
        assertEquals(true, scriptlerBuilder.isPropagateParams());
        assertThat(scriptlerBuilder.getParametersList(), hasSize(2));
        assertThat(scriptlerBuilder.getParametersList(), hasItems(new Parameter("param1", "value1"), new Parameter("param2", "value2")));
    }

    @Test
    @Issue("SECURITY-365")
    public void scriptStep_isNotModifiableThrough_webUI_byUserWithLowPrivilege() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.RUN_SCRIPTS).everywhere().to("scripter")
                .grant(Jenkins.READ, Item.READ, Item.CONFIGURE).everywhere().toEveryone()
        );

        checkModificationThroughWebUI("");
        checkModificationThroughWebUI("another-id");
    }

    private void checkModificationThroughWebUI(String builderId) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();

        { // admin can add a script step and modify it
            JenkinsRule.WebClient wc = j.createWebClient().login("admin");

            { // add
                HtmlPage page = postConfigWithOneBuilderLikeWebUI(wc, project, null, builderId, SCRIPT_USABLE_1);
                j.assertGoodStatus(page);

                verifyBuilderPresent(wc, project, SCRIPT_USABLE_1);
            }

            { // modify step
                HtmlPage page = postConfigWithOneBuilderLikeWebUI(wc, project, null, builderId, SCRIPT_USABLE_2);
                j.assertGoodStatus(page);

                verifyBuilderPresent(wc, project, SCRIPT_USABLE_2);
            }

            { // modify description
                HtmlPage page = postConfigWithOneBuilderLikeWebUI(wc, project, "desc_1", builderId, SCRIPT_USABLE_2);
                j.assertGoodStatus(page);

                verifyDescriptionChanged(wc, project, "desc_0", "desc_1");
            }
        }

        { // user cannot add a script neither edit an existing one, but can add a duplicate, modify other part
            JenkinsRule.WebClient wc = j.createWebClient().login("user");

            // if the builderId is empty when passed to web ui, a generated one is used
            String builderIdGenerated = !builderId.equals("") ? builderId : project.getBuildersList().get(ScriptlerBuilder.class).getBuilderId();

            { // can edit description (or other field)
                HtmlPage page = postConfigWithOneBuilderLikeWebUI(wc, project, "desc_2", builderIdGenerated, SCRIPT_USABLE_2);
                j.assertGoodStatus(page);

                verifyDescriptionChanged(wc, project, "desc_1", "desc_2");
            }

            { // cannot add new
                try {
                    // builderID + "random-id" => to generate a new builderId that does not exist in the existing project
                    postConfigWithMultipleBuildersLikeWebUI(wc, project, null, builderIdGenerated, SCRIPT_USABLE_2, builderIdGenerated + "random-id", SCRIPT_USABLE_3);
                    fail();
                } catch (FailingHttpStatusCodeException e) {
                    assertEquals(400, e.getStatusCode());
                    assertTrue(e.getResponse().getContentAsString().contains("builderId: The builderId must correspond to an existing builder of that project since the user does not have the rights to add/edit Scriptler step"));
                }

                verifyBuilderNotPresent(wc, project, SCRIPT_USABLE_3);
                verifyBuilderPresent(wc, project, SCRIPT_USABLE_2);
            }

            { // cannot modify existing
                try {
                    postConfigWithOneBuilderLikeWebUI(wc, project, null, builderIdGenerated, SCRIPT_USABLE_3);
                    fail();
                } catch (FailingHttpStatusCodeException e) {
                    assertEquals(400, e.getStatusCode());
                    assertTrue(e.getResponse().getContentAsString().contains("builderId: The builderId must correspond to an existing builder of that project since the user does not have the rights to add/edit Scriptler step"));
                }

                verifyBuilderNotPresent(wc, project, SCRIPT_USABLE_3);
                verifyBuilderPresent(wc, project, SCRIPT_USABLE_2);
            }

            { // add a clone (CLONE_NOTE: see note in class javadoc)
                assertEquals(1, getNumberOfScriptlerBuilder(project));
                HtmlPage page = postConfigWithMultipleBuildersLikeWebUI(wc, project, null, builderIdGenerated, SCRIPT_USABLE_2, builderIdGenerated, SCRIPT_USABLE_2);
                j.assertGoodStatus(page);

                assertEquals(2, getNumberOfScriptlerBuilder(project));
                verifyBuilderPresent(wc, project, SCRIPT_USABLE_2);
            }
        }
    }

    @Test
    @Issue("SECURITY-365")
    public void scriptStep_isNotModifiableThrough_configXml_byUserWithLowPrivilege() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.RUN_SCRIPTS).everywhere().to("scripter")
                .grant(Jenkins.READ, Item.READ, Item.CONFIGURE).everywhere().toEveryone()
        );

        checkScriptModification();
        checkScriptCreation();
    }

    private void checkScriptModification() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("test-modification");
        project.setDescription("desc_0");
        project.getBuildersList().add(new ScriptlerBuilder("random-id", SCRIPT_USABLE_1, false, Collections.emptyList()));
        project.getBuildersList().add(new ScriptlerBuilder("", SCRIPT_USABLE_4, false, Collections.emptyList()));

        // only one in this scenario
        assertEquals(project.getBuildersList().get(ScriptlerBuilder.class).getScriptId(), SCRIPT_USABLE_1);

        // user with ADMINISTER can modify configuration
        JenkinsRule.WebClient wc = j.createWebClient().login("admin");
        canChangeScriptUsingConfigXml(wc, project, SCRIPT_USABLE_1, SCRIPT_USABLE_2);
        canChangeDescriptionUsingConfigXml(wc, project, "desc_0", "desc_1");

        // user with lower privilege cannot modify the script
        wc = j.createWebClient().login("user");
        cannotChangeScriptUsingConfigXml(wc, project, SCRIPT_USABLE_2, SCRIPT_USABLE_1);
        // but can still modify the other part of the project
        canChangeDescriptionUsingConfigXml(wc, project, "desc_1", "desc_2");

        // user with RUN_SCRIPT can modify configuration
        wc = j.createWebClient().login("scripter");
        canChangeScriptUsingConfigXml(wc, project, SCRIPT_USABLE_2, SCRIPT_USABLE_3);
        canChangeDescriptionUsingConfigXml(wc, project, "desc_2", "desc_3");
    }

    private void checkScriptCreation() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("test-creation");

        // user with ADMINISTER can modify configuration
        JenkinsRule.WebClient wc = j.createWebClient().login("admin");
        canAddScriptUsingConfigXml(wc, project, "", SCRIPT_USABLE_1);
        // second script with "" as builderId, (CLONE_NOTE: see note in class javadoc)
        canAddScriptUsingConfigXml(wc, project, "", SCRIPT_USABLE_2);
        canAddScriptUsingConfigXml(wc, project, "builder-1", SCRIPT_USABLE_3);

        // user with lower privilege cannot add a script
        wc = j.createWebClient().login("user");
        // /!\ will create a duplicate, which does not seems to be dangerous
        canAddScriptUsingConfigXml(wc, project, "", SCRIPT_USABLE_1);
        // no existing script correspond to it
        cannotAddScriptUsingConfigXml(wc, project, "", SCRIPT_USABLE_4);
        cannotAddScriptUsingConfigXml(wc, project, "builder-2", SCRIPT_USABLE_4);

        // user with RUN_SCRIPT can modify configuration
        wc = j.createWebClient().login("scripter");
        canAddScriptUsingConfigXml(wc, project, "", SCRIPT_USABLE_3);
        canAddScriptUsingConfigXml(wc, project, "builder-3", SCRIPT_USABLE_1);
    }

    @Test
    @Issue("SECURITY-366")
    public void testCheckData_checkScriptUsability() throws Exception {
        Map<String, String> errors;
        ScriptlerBuilder builder;

        { // no care directly of the builderId
            builder = new ScriptlerBuilder("given-id", SCRIPT_USABLE_1, false, Collections.emptyList());
            errors = callCheckData(builder);
            assertTrue(errors.isEmpty());

            builder = new ScriptlerBuilder(null, SCRIPT_USABLE_1, false, Collections.emptyList());
            errors = callCheckData(builder);
            assertTrue(errors.isEmpty());

            builder = new ScriptlerBuilder("", SCRIPT_USABLE_1, false, Collections.emptyList());
            errors = callCheckData(builder);
            assertTrue(errors.isEmpty());
        }

        // concern especially SECURITY-366
        builder = new ScriptlerBuilder("", SCRIPT_NOT_USABLE, false, Collections.emptyList());
        errors = callCheckData(builder);
        assertEquals(1, errors.size());
        assertTrue(errors.containsKey("scriptId"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> callCheckData(ScriptlerBuilder builder) throws Exception {
        Method checkGenericData = ScriptlerBuilder.class.getDeclaredMethod("checkGenericData");
        checkGenericData.setAccessible(true);
        return (Map<String, String>) checkGenericData.invoke(builder);
    }

    @Test
    @Issue("SECURITY-366")
    public void scriptNotUsableInStep_isNotInjectableBy_webUI() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        JenkinsRule.WebClient wc = j.createWebClient();

        { // try adding a script that is not authorized in step
            try {
                postConfigWithOneBuilderLikeWebUI(wc, project, null, "", SCRIPT_NOT_USABLE);
                fail();
            } catch (FailingHttpStatusCodeException e) {
                assertEquals(400, e.getStatusCode());
                assertTrue(e.getResponse().getContentAsString().contains("scriptId: The script is not allowed to be executed in a build, check its configuration!"));
            }

            verifyNoBuilder(wc, project);
        }

        { // add a valid script
            HtmlPage page = postConfigWithOneBuilderLikeWebUI(wc, project, null, "", SCRIPT_USABLE_1);
            j.assertGoodStatus(page);

            verifyBuilderPresent(wc, project, SCRIPT_USABLE_1);
        }

        { // try modifying the script to one that is invalid
            try {
                postConfigWithOneBuilderLikeWebUI(wc, project, null, "", SCRIPT_NOT_USABLE);
                fail();
            } catch (FailingHttpStatusCodeException e) {
                assertEquals(400, e.getStatusCode());
                assertTrue(e.getResponse().getContentAsString().contains("scriptId: The script is not allowed to be executed in a build, check its configuration!"));
            }

            verifyBuilderNotPresent(wc, project, SCRIPT_NOT_USABLE);
            verifyBuilderPresent(wc, project, SCRIPT_USABLE_1);
        }

        { // modifying to another valid script
            HtmlPage page = postConfigWithOneBuilderLikeWebUI(wc, project, null, "", SCRIPT_USABLE_2);
            j.assertGoodStatus(page);

            verifyBuilderNotPresent(wc, project, SCRIPT_USABLE_1);
            verifyBuilderPresent(wc, project, SCRIPT_USABLE_2);
        }
    }

    @Test
    @Issue("SECURITY-366")
    public void scriptNotUsableInStep_isNotInjectableBy_configXml() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setCrumbIssuer(null);

        JenkinsRule.WebClient wc = j.createWebClient();
        FreeStyleProject project = j.createFreeStyleProject("test");

        // configure project without script step
        assertTrue(project.getBuildersList().isEmpty());

        // try adding forbidden script (not usable in step)
        cannotAddScriptUsingConfigXml(wc, project, "random-id", SCRIPT_NOT_USABLE);

        // add a valid script
        canAddScriptUsingConfigXml(wc, project, "random-id", SCRIPT_USABLE_1);

        // try modifying the script to another that is invalid
        cannotChangeScriptUsingConfigXml(wc, project, SCRIPT_USABLE_1, SCRIPT_NOT_USABLE);

        // modify the script to another that is valid
        canChangeScriptUsingConfigXml(wc, project, SCRIPT_USABLE_1, SCRIPT_USABLE_2);
    }

    private HtmlPage postConfigWithOneBuilderLikeWebUI(
            JenkinsRule.WebClient wc, final FreeStyleProject project,
            @CheckForNull final String newDescription,
            final String builderId, final String scriptId
    ) throws Exception {
        return postConfigWithMultipleBuildersLikeWebUI(wc, project, newDescription, builderId, scriptId);
    }

    private HtmlPage postConfigWithMultipleBuildersLikeWebUI(
            JenkinsRule.WebClient wc, final FreeStyleProject project,
            @CheckForNull final String newDescription,
            final String... builderScriptAlternateId
    ) throws Exception {
        assertTrue(builderScriptAlternateId.length % 2 == 0);

        final List<Map<String, Object>> builders = new ArrayList<>();
        for (int i = 0; i < builderScriptAlternateId.length; i += 2) {
            final int fi = i;
            builders.add(new HashMap<String, Object>() {{
                put("kind", ScriptlerBuilder.class.getName());
                put("builderId", builderScriptAlternateId[fi]);
                put("scriptlerScriptId", builderScriptAlternateId[fi + 1]);
                put("propagateParams", true);
            }});
        }

        final String description = newDescription != null ? newDescription : project.getDescription();

        WebRequest request = new WebRequest(new URL(j.getURL() + project.getShortUrl() + "configSubmit"), HttpMethod.POST);
        request.setRequestParameters(Arrays.asList(
                new NameValuePair("description", description),
                new NameValuePair("json", JSONObject.fromObject(
                        new HashMap<String, Object>() {{
                            put("name", project.getName());
                            put("description", description);
                            put("builder", builders);
                        }}
                ).toString())
        ));

        return wc.getPage(request);
    }

    private void verifyNoBuilder(JenkinsRule.WebClient wc, FreeStyleProject project) throws Exception {
        // verification the script is not stored in memory
        assertTrue(project.getBuildersList().isEmpty());
        project = refreshProject(project);
        assertTrue(project.getBuildersList().isEmpty());

        // nor in config.xml file
        String xml = retrieveXmlConfigForProject(wc, project);
        assertFalse(xml.contains("ScriptlerBuilder"));
    }

    private void verifyBuilderPresent(JenkinsRule.WebClient wc, FreeStyleProject project, String scriptId) throws Exception {
        _assertBuilderWithScriptId(project, scriptId);
        project = refreshProject(project);
        _assertBuilderWithScriptId(project, scriptId);

        String xmlVerification = retrieveXmlConfigForProject(wc, project);
        assertTrue(xmlVerification.contains(scriptId));
    }

    private void verifyBuilderNotPresent(JenkinsRule.WebClient wc, FreeStyleProject project, String scriptId) throws Exception {
        _assertNoBuilderWithScriptId(project, scriptId);
        project = refreshProject(project);
        _assertNoBuilderWithScriptId(project, scriptId);

        String xmlVerification = retrieveXmlConfigForProject(wc, project);
        assertFalse(xmlVerification.contains(scriptId));
    }

    private void _assertBuilderWithScriptId(FreeStyleProject project, String scriptId) {
        for (ScriptlerBuilder scriptlerBuilder : project.getBuildersList().getAll(ScriptlerBuilder.class)) {
            if (scriptlerBuilder.getScriptId().equals(scriptId)) {
                return;
            }
        }
        fail();
    }

    private void _assertNoBuilderWithScriptId(FreeStyleProject project, String scriptId) {
        for (ScriptlerBuilder scriptlerBuilder : project.getBuildersList().getAll(ScriptlerBuilder.class)) {
            assertNotEquals(scriptId, scriptlerBuilder.getScriptId());
        }
    }

    private String retrieveXmlConfigForProject(JenkinsRule.WebClient wc, Project<?, ?> p) throws Exception {
        XmlPage xmlPage = wc.goToXml(p.getShortUrl() + "config.xml");
        j.assertGoodStatus(xmlPage);
        return xmlPage.getWebResponse().getContentAsString();
    }

    private HtmlPage postXmlConfigForProject(JenkinsRule.WebClient wc, Project<?, ?> p, String xml) throws Exception {
        WebRequest request = new WebRequest(new URL(p.getAbsoluteUrl() + "config.xml"), HttpMethod.POST);
        request.setRequestBody(xml);
        request.setEncodingType(null);

        return wc.getPage(request);
    }

    private static final String XML_SINGLE_BUILDER_TEMPLATE = "" +
            "  <org.jenkinsci.plugins.scriptler.builder.ScriptlerBuilder>\n" +
            "    <builderId>%s</builderId>\n" +
            "    <scriptId>%s</scriptId>\n" +
            "  </org.jenkinsci.plugins.scriptler.builder.ScriptlerBuilder>";

    private void canChangeScriptUsingConfigXml(JenkinsRule.WebClient wc, FreeStyleProject project, String currentScriptId, String desiredScriptId) throws Exception {
        String xml = retrieveXmlConfigForProject(wc, project);
        assertTrue(xml.contains(currentScriptId));
        String modifiedXml = xml.replace("<scriptId>" + currentScriptId + "</scriptId>", "<scriptId>" + desiredScriptId + "</scriptId>");

        HtmlPage p = postXmlConfigForProject(wc, project, modifiedXml);
        j.assertGoodStatus(p);

        verifyBuilderPresent(wc, project, desiredScriptId);
        if (!currentScriptId.equals(desiredScriptId)) {
            verifyBuilderNotPresent(wc, project, currentScriptId);
        }
    }

    private void cannotChangeScriptUsingConfigXml(JenkinsRule.WebClient wc, FreeStyleProject project, String currentScriptId, String desiredScriptId) throws Exception {
        String xml = retrieveXmlConfigForProject(wc, project);
        assertTrue(xml.contains(currentScriptId));
        String modifiedXml = xml.replace("<scriptId>" + currentScriptId + "</scriptId>", "<scriptId>" + desiredScriptId + "</scriptId>");

        try {
            postXmlConfigForProject(wc, project, modifiedXml);
            fail();
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(500, e.getStatusCode());
        }

        verifyBuilderPresent(wc, project, currentScriptId);
        verifyBuilderNotPresent(wc, project, desiredScriptId);
    }

    private HtmlPage _addScriptUsingConfigXml(JenkinsRule.WebClient wc, FreeStyleProject project, String builderId, String scriptId) throws Exception {
        String xml = retrieveXmlConfigForProject(wc, project);
        String modifiedXml;
        if (xml.contains("<builders/>")) {
            // no builder, we can replace the whole block
            modifiedXml = xml.replace("<builders/>", "<builders>" + String.format(XML_SINGLE_BUILDER_TEMPLATE, builderId, scriptId) + "</builders>");
        } else {
            // existing builder, we replace only the closing tag
            assertTrue(xml.contains("</builders>"));
            modifiedXml = xml.replace("</builders>", String.format(XML_SINGLE_BUILDER_TEMPLATE, builderId, scriptId) + "</builders>");
        }

        return postXmlConfigForProject(wc, project, modifiedXml);
    }

    private void canAddScriptUsingConfigXml(JenkinsRule.WebClient wc, FreeStyleProject project, String builderId, String scriptId) throws Exception {
        int countBefore = getNumberOfScriptlerBuilder(project);

        HtmlPage page = _addScriptUsingConfigXml(wc, project, builderId, scriptId);
        j.assertGoodStatus(page);

        verifyBuilderPresent(wc, project, scriptId);

        int countAfter = getNumberOfScriptlerBuilder(refreshProject(project));
        assertEquals(countAfter, countBefore + 1);
    }

    private void cannotAddScriptUsingConfigXml(JenkinsRule.WebClient wc, FreeStyleProject project, String builderId, String scriptId) throws Exception {
        int countBefore = getNumberOfScriptlerBuilder(project);

        try {
            _addScriptUsingConfigXml(wc, project, builderId, scriptId);
            fail();
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(500, e.getStatusCode());
        }

        verifyBuilderNotPresent(wc, project, scriptId);

        int countAfter = getNumberOfScriptlerBuilder(refreshProject(project));
        assertEquals(countAfter, countBefore);
    }

    private int getNumberOfScriptlerBuilder(Project<?, ?> project) {
        return project.getBuildersList().getAll(ScriptlerBuilder.class).size();
    }

    private FreeStyleProject refreshProject(FreeStyleProject project) {
        return j.jenkins.getItemByFullName(project.getFullName(), FreeStyleProject.class);
    }

    private void canChangeDescriptionUsingConfigXml(JenkinsRule.WebClient wc, FreeStyleProject project, String currentDescription, String desiredDescription) throws Exception {
        HtmlPage p = _changeDescriptionUsingConfigXml(wc, project, currentDescription, desiredDescription);
        j.assertGoodStatus(p);

        verifyDescriptionChanged(wc, project, currentDescription, desiredDescription);
    }

    private HtmlPage _changeDescriptionUsingConfigXml(JenkinsRule.WebClient wc, FreeStyleProject project, String currentDescription, String desiredDescription) throws Exception {
        String xml = retrieveXmlConfigForProject(wc, project);
        String modifiedXml = xml.replace("<description>" + currentDescription + "</description>", "<description>" + desiredDescription + "</description>");

        return postXmlConfigForProject(wc, project, modifiedXml);
    }

    private void verifyDescriptionChanged(JenkinsRule.WebClient wc, FreeStyleProject project, String currentDescription, String desiredDescription) throws Exception {
        assertEquals(project.getDescription(), desiredDescription);
        project = refreshProject(project);
        assertEquals(project.getDescription(), desiredDescription);

        String xmlVerification = retrieveXmlConfigForProject(wc, project);
        assertTrue(xmlVerification.contains(desiredDescription));
        assertFalse(xmlVerification.contains(currentDescription));
    }
}
