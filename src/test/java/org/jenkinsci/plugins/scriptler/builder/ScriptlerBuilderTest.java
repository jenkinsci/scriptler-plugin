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
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import hudson.model.FileParameterValue;
import hudson.model.FreeStyleProject;
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

import java.io.File;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ScriptlerBuilderTest {

    private static final String SCRIPT_USABLE_1 = "usable_1.groovy";
    private static final String SCRIPT_USABLE_2 = "usable_2.groovy";
    private static final String SCRIPT_NOT_USABLE = "not_usable.groovy";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void configureScripts() throws Exception {
        ScriptlerManagement scriptler = j.getInstance().getExtensionList(ScriptlerManagement.class).get(0);
        ScriptlerManagementHelper helper = new ScriptlerManagementHelper(scriptler);

        File f1 = new File(SCRIPT_USABLE_1);
        FileUtils.writeStringToFile(f1, "print 'test1'");
        FileItem fi1 = new FileParameterValue.FileItemImpl(f1);
        helper.saveScript(fi1, true, SCRIPT_USABLE_1);
        f1.delete();

        File f2 = new File(SCRIPT_USABLE_2);
        FileUtils.writeStringToFile(f2, "print 'test2'");
        FileItem fi2 = new FileParameterValue.FileItemImpl(f2);
        helper.saveScript(fi2, true, SCRIPT_USABLE_2);
        f2.delete();

        File f3 = new File(SCRIPT_NOT_USABLE);
        FileUtils.writeStringToFile(f3, "print 'test3'");
        FileItem fi3 = new FileParameterValue.FileItemImpl(f3);
        helper.saveScript(fi3, false, SCRIPT_NOT_USABLE);
        f3.delete();
    }

    @Test
    @Issue("SECURITY-366")
    public void scriptNotUsableByNonAdmin_isNotInjectableBy_configSubmit() throws Exception {
        // use the same flow as a call to submit in the config page

        FreeStyleProject projectOk = j.createFreeStyleProject("testOk");
        projectOk.getBuildersList().add(new ScriptlerBuilder("random-id1", SCRIPT_USABLE_1, false, new Parameter[0]));

        assertNotEquals(projectOk.getBuildersList().get(ScriptlerBuilder.class).getScriptId(), SCRIPT_NOT_USABLE);
        projectOk = j.jenkins.getItemByFullName(projectOk.getFullName(), FreeStyleProject.class);
        assertNotEquals(projectOk.getBuildersList().get(ScriptlerBuilder.class).getScriptId(), SCRIPT_NOT_USABLE);

        FreeStyleProject projectNotOk = j.createFreeStyleProject("testNotOk");
        try {
            projectNotOk.getBuildersList().add(new ScriptlerBuilder("random-id2", SCRIPT_NOT_USABLE, false, new Parameter[0]));
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("The script is not allowed to be executed in a build, check its configuration!", e.getMessage());
        }

        assertTrue(projectNotOk.getBuildersList().isEmpty());
        projectNotOk = j.jenkins.getItemByFullName(projectNotOk.getFullName(), FreeStyleProject.class);
        assertTrue(projectNotOk.getBuildersList().isEmpty());
    }

    @Test
    @Issue("SECURITY-366")
    public void scriptNotUsableByNonAdmin_isNotInjectableBy_configXml() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setCrumbIssuer(null);

        FreeStyleProject project = j.createFreeStyleProject("test");
        project.getBuildersList().add(new ScriptlerBuilder("random-id", SCRIPT_USABLE_1, false, new Parameter[0]));

        JenkinsRule.WebClient wc = j.createWebClient();

        assertEquals(project.getBuildersList().get(ScriptlerBuilder.class).getScriptId(), SCRIPT_USABLE_1);

        XmlPage xmlPage = wc.goToXml(project.getShortUrl() + "config.xml");
        j.assertGoodStatus(xmlPage);
        String xml = xmlPage.getWebResponse().getContentAsString();
        String modifiedXml = xml.replace("<scriptId>" + SCRIPT_USABLE_1 + "</scriptId>", "<scriptId>" + SCRIPT_USABLE_2 + "</scriptId>");

        WebRequest request = new WebRequest(new URL(project.getAbsoluteUrl() + "config.xml"), HttpMethod.POST);
        request.setRequestBody(modifiedXml);
        request.setEncodingType(null);
        HtmlPage p = wc.getPage(request);
        j.assertGoodStatus(p);

        assertEquals(project.getBuildersList().get(ScriptlerBuilder.class).getScriptId(), SCRIPT_USABLE_2);
        project = j.jenkins.getItemByFullName(project.getFullName(), FreeStyleProject.class);
        assertEquals(project.getBuildersList().get(ScriptlerBuilder.class).getScriptId(), SCRIPT_USABLE_2);

        XmlPage xmlPage2 = wc.goToXml(project.getShortUrl() + "config.xml");
        j.assertGoodStatus(xmlPage2);
        String xml2 = xmlPage2.getWebResponse().getContentAsString();
        String modifiedXml2 = xml2.replace("<scriptId>" + SCRIPT_USABLE_2 + "</scriptId>", "<scriptId>" + SCRIPT_NOT_USABLE + "</scriptId>");

        WebRequest request2 = new WebRequest(new URL(project.getAbsoluteUrl() + "config.xml"), HttpMethod.POST);
        request2.setRequestBody(modifiedXml2);
        request2.setEncodingType(null);
        HtmlPage p2 = wc.getPage(request2);
        // the request is accepted but the invalid config is not applied, since the scriptId is not critical
        j.assertGoodStatus(p2);

        assertNotEquals(project.getBuildersList().get(ScriptlerBuilder.class).getScriptId(), SCRIPT_NOT_USABLE);
        project = j.jenkins.getItemByFullName(project.getFullName(), FreeStyleProject.class);
        assertNotEquals(project.getBuildersList().get(ScriptlerBuilder.class).getScriptId(), SCRIPT_NOT_USABLE);
    }
}
