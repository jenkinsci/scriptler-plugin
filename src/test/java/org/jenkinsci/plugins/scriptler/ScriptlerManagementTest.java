package org.jenkinsci.plugins.scriptler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.markup.MarkupFormatter;
import hudson.markup.RawHtmlMarkupFormatter;
import java.io.IOException;
import java.io.Writer;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ScriptlerManagementTest {

    @Test
    void markupFormatter(@SuppressWarnings("unused") JenkinsRule r) throws IOException {
        ScriptlerManagement management = new ScriptlerManagement();

        // save text
        String text = management.getMarkupFormatter().translate("Save text");
        assertEquals("Save text", text);

        // dangerous text with global formatter
        text = management.getMarkupFormatter().translate("<script>alert('PWND!')</script>");
        assertEquals("&lt;script&gt;alert(&#039;PWND!&#039;)&lt;/script&gt;", text);

        // dangerous text with OWASP formatter
        r.jenkins.setMarkupFormatter(RawHtmlMarkupFormatter.INSTANCE);
        text = management.getMarkupFormatter().translate("<script>alert('PWND!')</script>");
        assertEquals("", text);

        // save text with broken formatter
        MarkupFormatter formatter = new MarkupFormatter() {
            @Override
            public void translate(String markup, @NonNull Writer output) throws IOException {
                throw new IOException("Oh no!");
            }
        };
        r.jenkins.setMarkupFormatter(formatter);
        assertThrows(
                IOException.class, () -> management.getMarkupFormatter().translate("<script>alert('PWND!')</script>"));
    }
}
