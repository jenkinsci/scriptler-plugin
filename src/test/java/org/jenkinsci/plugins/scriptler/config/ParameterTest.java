package org.jenkinsci.plugins.scriptler.config;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class ParameterTest {
    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(Parameter.class).usingGetClass().verify();
    }
}
