package org.jenkinsci.plugins.scriptler;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeThat;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTextArea;
import org.htmlunit.html.HtmlTextInput;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class ScriptlerEncodingTest {
    private static final String SCRIPT_ID = "encodingTest.groovy";
    private static final String INTERNATIONALIZED_SCRIPT =
            "def myString = '3.2.0\u00df1'\n" + "println myString.replaceAll(/(\u00df|\\ RC\\ )/,'.')\n";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static Charset previousDefaultCharset;

    private void overwriteDefaultCharset() throws Exception {
        try {
            Field defaultCharset = Charset.class.getDeclaredField("defaultCharset");
            defaultCharset.setAccessible(true);
            previousDefaultCharset = (Charset) defaultCharset.get(null);
            defaultCharset.set(null, StandardCharsets.ISO_8859_1);
            assumeThat(Charset.defaultCharset().name(), is("ISO-8859-1"));
        } catch (Exception e) { // TODO Java9+ InaccessibleObjectException
            // Per JDK-4163515, this is not supported. It happened to work prior to Java 17, though.
            assumeNoException(e);
        }
    }

    private void restoreDefaultCharset() throws Exception {
        try {
            Field defaultCharset = Charset.class.getDeclaredField("defaultCharset");
            defaultCharset.setAccessible(true);
            defaultCharset.set(null, previousDefaultCharset);
        } catch (Exception e) { // TODO Java9+ InaccessibleObjectException
            // Per JDK-4163515, this is not supported. It happened to work prior to Java 17, though.
            assumeNoException(e);
        }
    }

    @Test
    @Issue("JENKINS-59841")
    public void testNonAsciiEncodingSaving() throws Exception {
        overwriteDefaultCharset();
        try {
            JenkinsRule.WebClient wc = j.createWebClient();

            HtmlPage scriptAddPage = wc.goTo("scriptler/scriptSettings");
            HtmlForm scriptAddForm = scriptAddPage.getFormByName("scriptAdd");
            ((HtmlTextInput) scriptAddForm.getInputByName("id")).setText(SCRIPT_ID);
            ((HtmlTextInput) scriptAddForm.getInputByName("name")).setText("Encoding Test");
            scriptAddForm.getInputByName("nonAdministerUsing").setChecked(true);
            scriptAddForm.getTextAreaByName("script").setText(INTERNATIONALIZED_SCRIPT);

            HtmlPage scriptAddPage1 = j.submit(scriptAddForm);
            j.assertGoodStatus(scriptAddPage1);

            HtmlPage showScriptPage = wc.goTo("scriptler/showScript?id=" + SCRIPT_ID);
            j.assertGoodStatus(showScriptPage);
            HtmlTextArea script = showScriptPage.getElementByName("script");
            assertEquals(INTERNATIONALIZED_SCRIPT, script.getText());
        } finally {
            restoreDefaultCharset();
        }
    }
}
