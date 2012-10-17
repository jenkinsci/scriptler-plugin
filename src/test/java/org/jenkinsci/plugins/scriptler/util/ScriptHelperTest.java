package org.jenkinsci.plugins.scriptler.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.scriptler.share.ScriptInfo;
import org.junit.Test;

public class ScriptHelperTest {

    @Test
    public void testGetJson() throws Exception {
        final String content = FileUtils.readFileToString(new File("src/test/resources/parsing_test.groovy"));
        assertTrue("no content from file", StringUtils.isNotBlank(content));

        final ScriptInfo info = ScriptHelper.extractScriptInfo(content);
        assertNotNull("ScriptInfo is null", info);
        assertEquals("1.300", info.getCore());
        assertEquals("print hello", info.getName());
        assertEquals("some cool comment", info.getComment());
        assertEquals("Dude mac", info.getAuthors().get(0).getName());
        assertEquals("param1", info.getParameters().get(0));
    }
}
