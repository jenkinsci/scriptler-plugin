package org.jenkinsci.plugins.scriptler.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.Configurator;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import org.jenkinsci.plugins.scriptler.ScriptlerManagement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
public class ScriptlerConfiguratorTest {

    private static final String IMPORT_SCRIPT_ID = "test-script.groovy";
    private static final String EXPORT_SCRIPT_ID = "export-test.groovy";
    private static final String EXPORT_SCRIPT_TEXT = "println 'Export'";
    private static final String EXPORT_SCRIPT_NAME = "Test script";

    @Test
    @ConfiguredWithCode("scriptler-config.yaml")
    public void testImportConfiguration(@SuppressWarnings("unused") JenkinsConfiguredWithCodeRule j) throws Exception {
        ScriptlerConfiguration config = getActiveConfiguration();

        assertTrue(config.isDisableRemoteCatalog(), "disableRemoteCatalog should be true");

        Set<Script> scripts = config.getScripts();
        assertEquals(1, scripts.size());
        assertEquals(IMPORT_SCRIPT_ID, scripts.iterator().next().getId());

        Path scriptFile = ScriptlerManagement.getScriptDirectory2().resolve(IMPORT_SCRIPT_ID);
        assertTrue(Files.exists(scriptFile), "Script file should exist on disk");

        String fileContent = Files.readString(scriptFile);
        assertTrue(fileContent.contains("println 'Hello World'"), "File content should match YAML source");
    }

    @Test
    public void testExportConfiguration(@SuppressWarnings("unused") JenkinsConfiguredWithCodeRule j) throws Exception {
        ScriptlerConfiguration config = createMockConfiguration();

        ConfigurationContext context = new ConfigurationContext(ConfiguratorRegistry.get());
        Configurator<ScriptlerConfiguration> configurator = context.lookupOrFail(ScriptlerConfiguration.class);
        assertNotNull(configurator, "JCasC failed to discover the ScriptlerConfigurator extension!");

        CNode cNode = configurator.describe(config, context);
        assertNotNull(cNode);
        Mapping rootMapping = cNode.asMapping();

        assertEquals("true", rootMapping.get("disableRemoteCatalog").asScalar().getValue());
        assertEquals(1, rootMapping.get("scripts").asSequence().size());

        Mapping exportedScript = rootMapping.get("scripts").asSequence().get(0).asMapping();
        assertScalarValue(EXPORT_SCRIPT_ID, exportedScript.get("id"));
        assertScalarValue(EXPORT_SCRIPT_TEXT, exportedScript.get("scriptText"));
        assertScalarValue(EXPORT_SCRIPT_NAME, exportedScript.get("name"));

        String fullExportedYaml = executeFullJCasCExport();
        assertTrue(fullExportedYaml.contains("scriptler:"), "Root element missing from full export");
        assertTrue(fullExportedYaml.contains(EXPORT_SCRIPT_ID), "Script ID missing from full export");
    }

    @AfterEach
    public void tearDown() {
        getActiveConfiguration().setScripts(new TreeSet<>());
    }

    private ScriptlerConfiguration getActiveConfiguration() {
        return ScriptlerConfiguration.getConfiguration();
    }

    private ScriptlerConfiguration createMockConfiguration() {
        ScriptlerConfiguration config = getActiveConfiguration();
        config.setDisableRemoteCatalog(true);

        Script script = new Script(
                EXPORT_SCRIPT_ID, EXPORT_SCRIPT_NAME, EXPORT_SCRIPT_TEXT, false, Collections.emptyList(), false);
        script.setScriptText(EXPORT_SCRIPT_TEXT);

        Set<Script> modifiableScripts = new TreeSet<>(config.getScripts());
        modifiableScripts.add(script);
        config.setScripts(modifiableScripts);
        return config;
    }

    private String executeFullJCasCExport() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConfigurationAsCode.get().export(out);
        return out.toString();
    }

    private void assertScalarValue(String expected, CNode scalarNode) {
        assertNotNull(scalarNode, "Target scalar node must not be null");
        assertEquals(expected, scalarNode.asScalar().getValue());
    }
}
