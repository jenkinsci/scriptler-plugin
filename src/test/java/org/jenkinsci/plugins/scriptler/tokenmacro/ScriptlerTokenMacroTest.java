package org.jenkinsci.plugins.scriptler.tokenmacro;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.scriptler.ScriptlerManagementHelper;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ScriptlerTokenMacroTest {

    @Test
    void testExecutesScript(JenkinsRule j) throws Exception {
        ScriptlerManagementHelper.saveScript("dummy.groovy", "return \"hello world ${build.number}\"", true);

        FreeStyleProject p = j.createFreeStyleProject("foo");
        FreeStyleBuild b = p.scheduleBuild2(0).get();

        final StreamTaskListener listener = StreamTaskListener.fromStdout();

        assertEquals("hello world 1", TokenMacro.expand(b, listener, "${SCRIPTLER,scriptId=\"dummy.groovy\"}"));
    }
}
