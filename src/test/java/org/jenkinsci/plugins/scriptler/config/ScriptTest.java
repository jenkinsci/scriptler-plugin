package org.jenkinsci.plugins.scriptler.config;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class ScriptTest {
    @Test
    void equalsContract() {
        EqualsVerifier.forClass(Script.class)
                .usingGetClass()
                .withOnlyTheseFields("id")
                .verify();
    }
}
