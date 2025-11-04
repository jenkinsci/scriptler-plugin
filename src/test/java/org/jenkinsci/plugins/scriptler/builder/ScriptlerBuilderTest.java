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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Project;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.htmlunit.xml.XmlPage;
import org.jenkinsci.plugins.scriptler.ScriptlerManagementHelper;
import org.jenkinsci.plugins.scriptler.ScriptlerPermissions;
import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Warning: a user without RUN_SCRIPT can currently only clone an existing builder INSIDE a project.
 * You can search CLONE_NOTE inside this test to see the cases
 */
@WithJenkins
class ScriptlerBuilderTest {
    private static final String ADMIN = "admin";
    private static final String SCRIPTLER = "scriptler";
    private static final String USER = "user";

    private static final String SCRIPT_CONTENTS = "print 'Hello World!'";
    private static final String SCRIPT_USABLE_1 = "script_usable_1.groovy";
    private static final String SCRIPT_USABLE_2 = "script_usable_2.groovy";
    private static final String SCRIPT_USABLE_3 = "script_usable_3.groovy";
    private static final String SCRIPT_USABLE_4 = "script_usable_4.groovy";

    private static final String SCRIPT_NOT_USABLE = "not_usable.groovy";

    private static final String MISMATCHED_BUILDER_ID =
            "builderId: The builderId must correspond to an existing builder of that project since the user does not have the rights to add/edit Scriptler step";

    private JenkinsRule j;

    @BeforeEach
    void setupScripts(JenkinsRule j) throws Exception {
        this.j = j;
        j.jenkins.setCrumbIssuer(null);

        ScriptlerManagementHelper.saveScript(SCRIPT_USABLE_1, SCRIPT_CONTENTS, true);
        ScriptlerManagementHelper.saveScript(SCRIPT_USABLE_2, SCRIPT_CONTENTS, true);
        ScriptlerManagementHelper.saveScript(SCRIPT_USABLE_3, SCRIPT_CONTENTS, true);
        ScriptlerManagementHelper.saveScript(SCRIPT_USABLE_4, SCRIPT_CONTENTS, true);
        ScriptlerManagementHelper.saveScript(SCRIPT_NOT_USABLE, SCRIPT_CONTENTS, false);
    }

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(ScriptlerBuilder.class).usingGetClass().verify();
    }

    @Test
    void configRoundTripWebUI() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("test");

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            WebRequest request =
                    new WebRequest(new URL(j.getURL() + project.getShortUrl() + "configSubmit"), HttpMethod.POST);

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
            j.assertGoodStatus(page);
        }

        ScriptlerBuilder scriptlerBuilder =
                refreshProject(project).getBuildersList().get(ScriptlerBuilder.class);
        assertNotNull(scriptlerBuilder);
        assertNotNull(scriptlerBuilder.getBuilderId());
        assertEquals(SCRIPT_USABLE_1, scriptlerBuilder.getScriptId());
        assertTrue(scriptlerBuilder.isPropagateParams());
        assertIterableEquals(
                List.of(new Parameter("param1", "value1"), new Parameter("param2", "value2")),
                scriptlerBuilder.getParametersList());
    }

    @Test
    void configRoundTripConfigXml() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("test");

        try (JenkinsRule.WebClient wc = j.createWebClient()) {

            String xml = retrieveXmlConfigForProject(wc, project);
            String modifiedXml = xml.replace("<builders/>", String.format("""
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
                            </builders>""", SCRIPT_USABLE_1));

            HtmlPage page = postXmlConfigForProject(wc, project, modifiedXml);
            j.assertGoodStatus(page);

            ScriptlerBuilder scriptlerBuilder =
                    refreshProject(project).getBuildersList().get(ScriptlerBuilder.class);
            assertNotNull(scriptlerBuilder);
            assertEquals("", scriptlerBuilder.getBuilderId());
            assertEquals(SCRIPT_USABLE_1, scriptlerBuilder.getScriptId());
            assertTrue(scriptlerBuilder.isPropagateParams());
            assertIterableEquals(
                    List.of(new Parameter("param1", "value1"), new Parameter("param2", "value2")),
                    scriptlerBuilder.getParametersList());
        }
    }

    @Test
    @Issue("SECURITY-365")
    void scriptStep_isNotModifiableThrough_webUI_byUserWithLowPrivilege() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER, ScriptlerPermissions.CONFIGURE)
                .everywhere()
                .to(ADMIN)
                .grant(ScriptlerPermissions.CONFIGURE)
                .everywhere()
                .to(SCRIPTLER)
                .grant(Jenkins.READ, Item.READ, Item.CONFIGURE, Item.CREATE)
                .everywhere()
                .toEveryone());

        checkModificationThroughWebUI("");
        checkModificationThroughWebUI("another-id");
    }

    private void checkModificationThroughWebUI(@NonNull String builderId) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();

        try (JenkinsRule.WebClient wc = j.createWebClient().login(ADMIN)) {
            // admin can add a script step and modify it
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

        try (JenkinsRule.WebClient wc = j.createWebClient().login(USER)) {
            // user cannot add a script neither edit an existing one, but can add a duplicate, modify other part
            // if the builderId is empty when passed to web ui, a generated one is used
            String builderIdGenerated = builderId.isEmpty()
                    ? project.getBuildersList().get(ScriptlerBuilder.class).getBuilderId()
                    : builderId;

            { // can edit description (or other field)
                HtmlPage page =
                        postConfigWithOneBuilderLikeWebUI(wc, project, "desc_2", builderIdGenerated, SCRIPT_USABLE_2);
                j.assertGoodStatus(page);

                verifyDescriptionChanged(wc, project, "desc_1", "desc_2");
            }

            { // cannot add new
                // builderID + "random-id" => to generate a new builderId that does not exist in the existing project
                FailingHttpStatusCodeException e = assertThrows(
                        FailingHttpStatusCodeException.class,
                        () -> postConfigWithMultipleBuildersLikeWebUI(
                                wc,
                                project,
                                null,
                                builderIdGenerated,
                                SCRIPT_USABLE_2,
                                builderIdGenerated + "random-id",
                                SCRIPT_USABLE_3));
                assertEquals(400, e.getStatusCode());
                assertTrue(e.getResponse().getContentAsString().contains(MISMATCHED_BUILDER_ID));

                verifyBuilderNotPresent(wc, project, SCRIPT_USABLE_3);
                verifyBuilderPresent(wc, project, SCRIPT_USABLE_2);
            }

            { // cannot modify existing
                FailingHttpStatusCodeException e = assertThrows(
                        FailingHttpStatusCodeException.class,
                        () -> postConfigWithOneBuilderLikeWebUI(
                                wc, project, null, builderIdGenerated, SCRIPT_USABLE_3));
                assertEquals(400, e.getStatusCode());
                assertTrue(e.getResponse().getContentAsString().contains(MISMATCHED_BUILDER_ID));

                verifyBuilderNotPresent(wc, project, SCRIPT_USABLE_3);
                verifyBuilderPresent(wc, project, SCRIPT_USABLE_2);
            }

            { // add a clone (CLONE_NOTE: see note in class javadoc)
                assertEquals(1, getNumberOfScriptlerBuilder(project));
                HtmlPage page = postConfigWithMultipleBuildersLikeWebUI(
                        wc, project, null, builderIdGenerated, SCRIPT_USABLE_2, builderIdGenerated, SCRIPT_USABLE_2);
                j.assertGoodStatus(page);

                assertEquals(2, getNumberOfScriptlerBuilder(project));
                verifyBuilderPresent(wc, project, SCRIPT_USABLE_2);
            }
        }
    }

    @Test
    @Issue("SECURITY-365")
    void scriptStep_isNotModifiableThrough_configXml_byUserWithLowPrivilege() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER, ScriptlerPermissions.CONFIGURE)
                .everywhere()
                .to(ADMIN)
                .grant(ScriptlerPermissions.CONFIGURE)
                .everywhere()
                .to(SCRIPTLER)
                .grant(Jenkins.READ, Item.READ, Item.CONFIGURE, Item.CREATE)
                .everywhere()
                .toEveryone());

        checkScriptModification();
        checkScriptCreation();
    }

    private void checkScriptModification() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("test-modification");
        project.setDescription("desc_0");
        project.getBuildersList().add(new ScriptlerBuilder("random-id", SCRIPT_USABLE_1, false, List.of()));
        project.getBuildersList().add(new ScriptlerBuilder("", SCRIPT_USABLE_4, false, List.of()));

        // only one in this scenario
        assertEquals(
                SCRIPT_USABLE_1,
                project.getBuildersList().get(ScriptlerBuilder.class).getScriptId());

        // user with ADMINISTER can modify configuration
        try (JenkinsRule.WebClient wc = j.createWebClient().login(ADMIN)) {
            canChangeScriptUsingConfigXml(wc, project, SCRIPT_USABLE_1, SCRIPT_USABLE_2);
            canChangeDescriptionUsingConfigXml(wc, project, "desc_0", "desc_1");
        }

        // user with lower privilege cannot modify the script
        try (JenkinsRule.WebClient wc = j.createWebClient().login(USER)) {
            cannotChangeScriptUsingConfigXml(wc, project, SCRIPT_USABLE_2, SCRIPT_USABLE_1);
            // but can still modify the other part of the project
            canChangeDescriptionUsingConfigXml(wc, project, "desc_1", "desc_2");
        }

        // user with RUN_SCRIPT can modify configuration
        try (JenkinsRule.WebClient wc = j.createWebClient().login(SCRIPTLER)) {
            canChangeScriptUsingConfigXml(wc, project, SCRIPT_USABLE_2, SCRIPT_USABLE_3);
            canChangeDescriptionUsingConfigXml(wc, project, "desc_2", "desc_3");
        }
    }

    private void checkScriptCreation() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("test-creation");

        // user with ADMINISTER can modify configuration
        try (JenkinsRule.WebClient wc = j.createWebClient().login(ADMIN)) {
            canAddScriptUsingConfigXml(wc, project, "", SCRIPT_USABLE_1);
            // second script with "" as builderId, (CLONE_NOTE: see note in class javadoc)
            canAddScriptUsingConfigXml(wc, project, "", SCRIPT_USABLE_2);
            canAddScriptUsingConfigXml(wc, project, "builder-1", SCRIPT_USABLE_3);
        }

        // user with lower privilege cannot add a script
        try (JenkinsRule.WebClient wc = j.createWebClient().login(USER)) {
            // /!\ will create a duplicate, which does not seems to be dangerous
            canAddScriptUsingConfigXml(wc, project, "", SCRIPT_USABLE_1);
            // no existing script correspond to it
            cannotAddScriptUsingConfigXml(wc, project, "", SCRIPT_USABLE_4);
            cannotAddScriptUsingConfigXml(wc, project, "builder-2", SCRIPT_USABLE_4);
        }

        // user with RUN_SCRIPT can modify configuration
        try (JenkinsRule.WebClient wc = j.createWebClient().login(SCRIPTLER)) {
            canAddScriptUsingConfigXml(wc, project, "", SCRIPT_USABLE_3);
            canAddScriptUsingConfigXml(wc, project, "builder-3", SCRIPT_USABLE_1);
        }
    }

    @Test
    @Issue("SECURITY-366")
    void testCheckData_checkScriptUsability() throws Exception {
        Map<String, String> errors;
        ScriptlerBuilder builder;

        { // no care directly of the builderId
            builder = new ScriptlerBuilder("given-id", SCRIPT_USABLE_1, false, List.of());
            errors = callCheckData(builder);
            assertTrue(errors.isEmpty());

            builder = new ScriptlerBuilder(null, SCRIPT_USABLE_1, false, List.of());
            errors = callCheckData(builder);
            assertTrue(errors.isEmpty());

            builder = new ScriptlerBuilder("", SCRIPT_USABLE_1, false, List.of());
            errors = callCheckData(builder);
            assertTrue(errors.isEmpty());
        }

        // concern especially SECURITY-366
        builder = new ScriptlerBuilder("", SCRIPT_NOT_USABLE, false, List.of());
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
    void scriptNotUsableInStep_isNotInjectableBy_webUI() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            // try adding a script that is not authorized in step
            FailingHttpStatusCodeException e = assertThrows(
                    FailingHttpStatusCodeException.class,
                    () -> postConfigWithOneBuilderLikeWebUI(wc, project, null, "", SCRIPT_NOT_USABLE));
            assertEquals(400, e.getStatusCode());
            assertTrue(e.getResponse()
                    .getContentAsString()
                    .contains(
                            "scriptId: The script is not allowed to be executed in a build, check its configuration!"));

            verifyNoBuilder(wc, project);
        }

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            // add a valid script
            HtmlPage page = postConfigWithOneBuilderLikeWebUI(wc, project, null, "", SCRIPT_USABLE_1);
            j.assertGoodStatus(page);

            verifyBuilderPresent(wc, project, SCRIPT_USABLE_1);
        }

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            // try modifying the script to one that is invalid
            FailingHttpStatusCodeException e = assertThrows(
                    FailingHttpStatusCodeException.class,
                    () -> postConfigWithOneBuilderLikeWebUI(wc, project, null, "", SCRIPT_NOT_USABLE));
            assertEquals(400, e.getStatusCode());
            assertTrue(e.getResponse()
                    .getContentAsString()
                    .contains(
                            "scriptId: The script is not allowed to be executed in a build, check its configuration!"));

            verifyBuilderNotPresent(wc, project, SCRIPT_NOT_USABLE);
            verifyBuilderPresent(wc, project, SCRIPT_USABLE_1);
        }

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            // modifying to another valid script
            HtmlPage page = postConfigWithOneBuilderLikeWebUI(wc, project, null, "", SCRIPT_USABLE_2);
            j.assertGoodStatus(page);

            verifyBuilderNotPresent(wc, project, SCRIPT_USABLE_1);
            verifyBuilderPresent(wc, project, SCRIPT_USABLE_2);
        }
    }

    @Test
    @Issue("SECURITY-366")
    void scriptNotUsableInStep_isNotInjectableBy_configXml() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
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
    }

    private HtmlPage postConfigWithOneBuilderLikeWebUI(
            JenkinsRule.WebClient wc,
            final FreeStyleProject project,
            @CheckForNull final String newDescription,
            final String builderId,
            final String scriptId)
            throws Exception {
        return postConfigWithMultipleBuildersLikeWebUI(wc, project, newDescription, builderId, scriptId);
    }

    private HtmlPage postConfigWithMultipleBuildersLikeWebUI(
            JenkinsRule.WebClient wc,
            final FreeStyleProject project,
            @CheckForNull final String newDescription,
            final String... builderScriptAlternateId)
            throws Exception {
        assertEquals(0, builderScriptAlternateId.length % 2);

        final List<Map<String, Object>> builders = IntStream.iterate(
                        0, i -> i < builderScriptAlternateId.length, i -> i + 2)
                .mapToObj(i -> Map.<String, Object>of(
                        "kind",
                        ScriptlerBuilder.class.getName(),
                        "builderId",
                        builderScriptAlternateId[i],
                        "scriptlerScriptId",
                        builderScriptAlternateId[i + 1],
                        "propagateParams",
                        true))
                .toList();

        final String description = Optional.ofNullable(newDescription)
                .or(() -> Optional.ofNullable(project.getDescription()))
                .orElse("");

        WebRequest request =
                new WebRequest(new URL(j.getURL() + project.getShortUrl() + "configSubmit"), HttpMethod.POST);
        request.setRequestParameters(List.of(
                new NameValuePair("description", description),
                new NameValuePair(
                        "json",
                        JSONObject.fromObject(Map.of(
                                        "name", project.getName(), "description", description, "builder", builders))
                                .toString())));

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

    private void verifyBuilderPresent(JenkinsRule.WebClient wc, FreeStyleProject project, String scriptId)
            throws Exception {
        assertBuilderWithScriptId(project, scriptId);
        project = refreshProject(project);
        assertBuilderWithScriptId(project, scriptId);

        String xmlVerification = retrieveXmlConfigForProject(wc, project);
        assertTrue(xmlVerification.contains(scriptId));
    }

    private void verifyBuilderNotPresent(JenkinsRule.WebClient wc, FreeStyleProject project, String scriptId)
            throws Exception {
        assertNoBuilderWithScriptId(project, scriptId);
        project = refreshProject(project);
        assertNoBuilderWithScriptId(project, scriptId);

        String xmlVerification = retrieveXmlConfigForProject(wc, project);
        assertFalse(xmlVerification.contains(scriptId));
    }

    private boolean projectContainsScript(FreeStyleProject project, @NonNull String scriptId) {
        return project.getBuildersList().getAll(ScriptlerBuilder.class).stream()
                .map(ScriptlerBuilder::getScriptId)
                .anyMatch(scriptId::equals);
    }

    private void assertBuilderWithScriptId(FreeStyleProject project, @NonNull String scriptId) {
        assertTrue(projectContainsScript(project, scriptId), "Could not find a builder with script ID " + scriptId);
    }

    private void assertNoBuilderWithScriptId(FreeStyleProject project, String scriptId) {
        assertFalse(projectContainsScript(project, scriptId), "Found a builder with script ID " + scriptId);
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

    private static final String XML_SINGLE_BUILDER_TEMPLATE = """
                    <org.jenkinsci.plugins.scriptler.builder.ScriptlerBuilder>
                        <builderId>%s</builderId>
                        <scriptId>%s</scriptId>
                    </org.jenkinsci.plugins.scriptler.builder.ScriptlerBuilder>
                    """;

    private void canChangeScriptUsingConfigXml(
            JenkinsRule.WebClient wc, FreeStyleProject project, String currentScriptId, String desiredScriptId)
            throws Exception {
        String xml = retrieveXmlConfigForProject(wc, project);
        assertTrue(xml.contains(currentScriptId));
        String modifiedXml = xml.replace(
                "<scriptId>" + currentScriptId + "</scriptId>", "<scriptId>" + desiredScriptId + "</scriptId>");

        HtmlPage p = postXmlConfigForProject(wc, project, modifiedXml);
        j.assertGoodStatus(p);

        verifyBuilderPresent(wc, project, desiredScriptId);
        if (!currentScriptId.equals(desiredScriptId)) {
            verifyBuilderNotPresent(wc, project, currentScriptId);
        }
    }

    private void cannotChangeScriptUsingConfigXml(
            JenkinsRule.WebClient wc, FreeStyleProject project, String currentScriptId, String desiredScriptId)
            throws Exception {
        String xml = retrieveXmlConfigForProject(wc, project);
        assertTrue(xml.contains(currentScriptId));
        String modifiedXml = xml.replace(
                "<scriptId>" + currentScriptId + "</scriptId>", "<scriptId>" + desiredScriptId + "</scriptId>");

        FailingHttpStatusCodeException e = assertThrows(
                FailingHttpStatusCodeException.class, () -> postXmlConfigForProject(wc, project, modifiedXml));
        assertEquals(500, e.getStatusCode());

        verifyBuilderPresent(wc, project, currentScriptId);
        verifyBuilderNotPresent(wc, project, desiredScriptId);
    }

    private HtmlPage addScriptUsingConfigXml(
            JenkinsRule.WebClient wc, FreeStyleProject project, String builderId, String scriptId) throws Exception {
        String xml = retrieveXmlConfigForProject(wc, project);
        String modifiedXml;
        if (xml.contains("<builders/>")) {
            // no builder, we can replace the whole block
            modifiedXml = xml.replace(
                    "<builders/>",
                    "<builders>" + String.format(XML_SINGLE_BUILDER_TEMPLATE, builderId, scriptId) + "</builders>");
        } else {
            // existing builder, we replace only the closing tag
            assertTrue(xml.contains("</builders>"));
            modifiedXml = xml.replace(
                    "</builders>", String.format(XML_SINGLE_BUILDER_TEMPLATE, builderId, scriptId) + "</builders>");
        }

        return postXmlConfigForProject(wc, project, modifiedXml);
    }

    private void canAddScriptUsingConfigXml(
            JenkinsRule.WebClient wc, FreeStyleProject project, String builderId, String scriptId) throws Exception {
        int countBefore = getNumberOfScriptlerBuilder(project);

        HtmlPage page = addScriptUsingConfigXml(wc, project, builderId, scriptId);
        j.assertGoodStatus(page);

        verifyBuilderPresent(wc, project, scriptId);

        int countAfter = getNumberOfScriptlerBuilder(refreshProject(project));
        assertEquals(countAfter, countBefore + 1);
    }

    private void cannotAddScriptUsingConfigXml(
            JenkinsRule.WebClient wc, FreeStyleProject project, String builderId, String scriptId) throws Exception {
        int countBefore = getNumberOfScriptlerBuilder(project);

        FailingHttpStatusCodeException e = assertThrows(
                FailingHttpStatusCodeException.class, () -> addScriptUsingConfigXml(wc, project, builderId, scriptId));
        assertEquals(500, e.getStatusCode());

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

    private void canChangeDescriptionUsingConfigXml(
            JenkinsRule.WebClient wc, FreeStyleProject project, String currentDescription, String desiredDescription)
            throws Exception {
        HtmlPage p = changeDescriptionUsingConfigXml(wc, project, currentDescription, desiredDescription);
        j.assertGoodStatus(p);

        verifyDescriptionChanged(wc, project, currentDescription, desiredDescription);
    }

    private HtmlPage changeDescriptionUsingConfigXml(
            JenkinsRule.WebClient wc, FreeStyleProject project, String currentDescription, String desiredDescription)
            throws Exception {
        String xml = retrieveXmlConfigForProject(wc, project);
        String modifiedXml = xml.replace(
                "<description>" + currentDescription + "</description>",
                "<description>" + desiredDescription + "</description>");

        return postXmlConfigForProject(wc, project, modifiedXml);
    }

    private void verifyDescriptionChanged(
            JenkinsRule.WebClient wc, FreeStyleProject project, String currentDescription, String desiredDescription)
            throws Exception {
        assertEquals(project.getDescription(), desiredDescription);
        project = refreshProject(project);
        assertEquals(project.getDescription(), desiredDescription);

        String xmlVerification = retrieveXmlConfigForProject(wc, project);
        assertTrue(xmlVerification.contains(desiredDescription));
        assertFalse(xmlVerification.contains(currentDescription));
    }
}
