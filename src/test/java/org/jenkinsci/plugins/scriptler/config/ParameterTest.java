package org.jenkinsci.plugins.scriptler.config;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class ParameterTest {
    @Test
    void equalsContract() {
        EqualsVerifier.forClass(Parameter.class).usingGetClass().verify();
    }
}
