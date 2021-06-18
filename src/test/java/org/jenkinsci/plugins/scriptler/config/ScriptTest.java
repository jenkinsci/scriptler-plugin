package org.jenkinsci.plugins.scriptler.config;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Ignore;
import org.junit.Test;

public class ScriptTest {
    @Test
    @Ignore("Script needs more evaluation and reworking to fulfill this test")
    public void equalsContract() {
        EqualsVerifier.forClass(Script.class).usingGetClass().verify();
    }
}
