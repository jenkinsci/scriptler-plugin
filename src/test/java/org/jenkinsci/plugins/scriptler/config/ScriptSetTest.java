package org.jenkinsci.plugins.scriptler.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collections;
import org.junit.jupiter.api.Test;

public class ScriptSetTest {

    @Test
    public void testGetScriptsHandlesNull() {
        ScriptSet scriptSet = new ScriptSet();

        scriptSet.scriptSet = null;

        assertNotNull(scriptSet.getScripts(), "getScripts() should never return null");
        assertEquals(
                Collections.emptySet(), scriptSet.getScripts(), "Should return empty set when internal state is null");
    }

    @Test
    public void testSetScriptsHandlesNull() {
        ScriptSet scriptSet = new ScriptSet();
        scriptSet.scriptSet = null;

        scriptSet.setScripts(Collections.emptySet());

        assertNotNull(scriptSet.getScripts(), "Internal set should be initialized after setter");
    }
}
