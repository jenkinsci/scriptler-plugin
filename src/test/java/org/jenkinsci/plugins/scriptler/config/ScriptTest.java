package org.jenkinsci.plugins.scriptler.config;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class ScriptTest {
    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(Script.class)
                .usingGetClass()
                .withOnlyTheseFields("id")
                .verify();
    }
}
