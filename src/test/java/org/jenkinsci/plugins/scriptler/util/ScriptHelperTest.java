package org.jenkinsci.plugins.scriptler.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.jenkinsci.plugins.scriptler.share.ScriptInfo;
import org.junit.jupiter.api.Test;

class ScriptHelperTest {

    @Test
    void testGetJson() throws Exception {
        final String content =
                Files.readString(Paths.get("src/test/resources/parsing_test.groovy"), StandardCharsets.UTF_8);
        assertFalse(content.isBlank(), "no content from file");

        final ScriptInfo info = ScriptHelper.extractScriptInfo(content);
        assertNotNull(info, "ScriptInfo is null");
        assertEquals("1.300", info.getCore());
        assertEquals("print hello", info.getName());
        assertEquals("some cool comment", info.getComment());
        assertEquals("Dude mac", info.getAuthors().get(0).getName());
        assertEquals("param1", info.getParameters().get(0));
    }
}
