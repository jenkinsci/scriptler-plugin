package org.jenkinsci.plugins.scriptler.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.ExtensionList;
import io.jenkins.plugins.casc.Attribute;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.Configurator;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import io.jenkins.plugins.casc.model.Sequence;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import org.jenkinsci.plugins.scriptler.ScriptlerManagement;
import org.jenkinsci.plugins.scriptler.git.GitScriptlerRepository;
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

    @Test
    public void testDescribeAttributes(@SuppressWarnings("unused") JenkinsConfiguredWithCodeRule j) {
        ConfigurationContext context = new ConfigurationContext(ConfiguratorRegistry.get());
        Configurator<ScriptlerConfiguration> configurator = context.lookupOrFail(ScriptlerConfiguration.class);

        Set<Attribute<ScriptlerConfiguration, ?>> attributes = configurator.describe();

        assertNotNull(attributes, "Attributes set should not be null");
        assertEquals(2, attributes.size(), "Should expose exactly 2 configurable attributes");

        boolean hasDisableCatalog = attributes.stream().anyMatch(a -> "disableRemoteCatalog".equals(a.getName()));
        boolean hasScripts = attributes.stream().anyMatch(a -> "scripts".equals(a.getName()));

        assertTrue(hasDisableCatalog, "Metadata should expose 'disableRemoteCatalog'");
        assertTrue(hasScripts, "Metadata should expose 'scripts'");
    }

    @Test
    public void testCheckScriptsExpectsSequence(@SuppressWarnings("unused") JenkinsConfiguredWithCodeRule j) {
        ConfigurationContext context = new ConfigurationContext(ConfiguratorRegistry.get());
        Configurator<ScriptlerConfiguration> configurator = context.lookupOrFail(ScriptlerConfiguration.class);

        Mapping invalidMapping = new Mapping();
        invalidMapping.put("scripts", "not-a-sequence-list");

        assertThrows(
                ConfiguratorException.class,
                () -> configurator.check(invalidMapping, context),
                "The configurator should throw an exception if 'scripts' is not a sequence.");
    }

    @Test
    public void testConfigureDisableRemoteCatalogExpectsScalar(
            @SuppressWarnings("unused") JenkinsConfiguredWithCodeRule j) {
        ConfigurationContext context = new ConfigurationContext(ConfiguratorRegistry.get());
        Configurator<ScriptlerConfiguration> configurator = context.lookupOrFail(ScriptlerConfiguration.class);

        Mapping invalidMapping = new Mapping();
        invalidMapping.put("disableRemoteCatalog", new Mapping());

        assertThrows(
                ConfiguratorException.class,
                () -> configurator.configure(invalidMapping, context),
                "The configurator should throw an exception if 'disableRemoteCatalog' cannot be resolved to a scalar.");
    }

    @Test
    public void testConfigureScriptWithEmptyIdIsSkipped(@SuppressWarnings("unused") JenkinsConfiguredWithCodeRule j) {
        ConfigurationContext context = new ConfigurationContext(ConfiguratorRegistry.get());
        Configurator<ScriptlerConfiguration> configurator = context.lookupOrFail(ScriptlerConfiguration.class);

        Mapping scriptMapping = new Mapping();
        scriptMapping.put("id", "");
        scriptMapping.put("name", "Blank ID Test Script");
        scriptMapping.put("scriptText", "println 'Skip Me'");
        scriptMapping.put("parameters", new Sequence());

        Sequence scriptsSequence = new Sequence();
        scriptsSequence.add(scriptMapping);

        Mapping rootMapping = new Mapping();
        rootMapping.put("scripts", scriptsSequence);

        ScriptlerConfiguration config = configurator.configure(rootMapping, context);

        assertNotNull(config, "Configuration should return a valid instance");
        assertTrue(config.getScripts().isEmpty(), "Scripts list should remain empty because the blank ID was skipped");
    }

    @Test
    public void testConfigureRejectsDirectoryTraversal(@SuppressWarnings("unused") JenkinsConfiguredWithCodeRule j) {
        ConfigurationContext context = new ConfigurationContext(ConfiguratorRegistry.get());
        Configurator<ScriptlerConfiguration> configurator = context.lookupOrFail(ScriptlerConfiguration.class);

        Mapping scriptMapping = new Mapping();
        scriptMapping.put("id", "../../../traversal-exploit");
        scriptMapping.put("name", "Malicious Script");
        scriptMapping.put("scriptText", "println 'Exploit'");
        scriptMapping.put("parameters", new Sequence());

        Sequence scriptsSequence = new Sequence();
        scriptsSequence.add(scriptMapping);

        Mapping rootMapping = new Mapping();
        rootMapping.put("scripts", scriptsSequence);

        ConfiguratorException thrown = assertThrows(
                ConfiguratorException.class,
                () -> configurator.configure(rootMapping, context),
                "The configurator should reject folder traversal attempts.");

        assertTrue(
                thrown.getMessage().contains("Folder traversal detected"),
                "Error should report directory traversal protection");
    }

    @Test
    public void testConfigureHandlesGitRepositoryException(
            @SuppressWarnings("unused") JenkinsConfiguredWithCodeRule j) {
        ConfigurationContext context = new ConfigurationContext(ConfiguratorRegistry.get());
        Configurator<ScriptlerConfiguration> configurator = context.lookupOrFail(ScriptlerConfiguration.class);

        GitScriptlerRepository faultyRepo = new GitScriptlerRepository() {
            @Override
            public void addSingleFileToRepo(String fileName) {
                throw new RuntimeException("Simulated Git failure for testing catch block");
            }
        };

        ExtensionList<GitScriptlerRepository> repoList = ExtensionList.lookup(GitScriptlerRepository.class);
        repoList.add(0, faultyRepo);

        try {
            Mapping scriptMapping = new Mapping();
            scriptMapping.put("id", "git-fault-test.groovy");
            scriptMapping.put("name", "Git Test Script");
            scriptMapping.put("scriptText", "println 'Testing Git exception handling'");
            scriptMapping.put("parameters", new Sequence());

            Sequence scriptsSequence = new Sequence();
            scriptsSequence.add(scriptMapping);

            Mapping rootMapping = new Mapping();
            rootMapping.put("scripts", scriptsSequence);

            ScriptlerConfiguration config = configurator.configure(rootMapping, context);

            assertNotNull(config);
            assertEquals(
                    1,
                    config.getScripts().size(),
                    "Configuration should succeed despite Git repository logging errors");
        } finally {
            repoList.remove(faultyRepo);
        }
    }

    @Test
    public void testConfigureHandlesWriteException(@SuppressWarnings("unused") JenkinsConfiguredWithCodeRule j)
            throws Exception {
        ConfigurationContext context = new ConfigurationContext(ConfiguratorRegistry.get());
        Configurator<ScriptlerConfiguration> configurator = context.lookupOrFail(ScriptlerConfiguration.class);

        String scriptId = "write-fault-test.groovy";
        Path scriptlerDir = ScriptlerManagement.getScriptDirectory2();
        Path conflictingDir = scriptlerDir.resolve(scriptId);

        Files.createDirectories(conflictingDir);

        try {
            Mapping scriptMapping = new Mapping();
            scriptMapping.put("id", scriptId);
            scriptMapping.put("name", "Write Fault Script");
            scriptMapping.put("scriptText", "println 'This write should fail'");
            scriptMapping.put("parameters", new Sequence());

            Sequence scriptsSequence = new Sequence();
            scriptsSequence.add(scriptMapping);

            Mapping rootMapping = new Mapping();
            rootMapping.put("scripts", scriptsSequence);

            ConfiguratorException thrown = assertThrows(
                    ConfiguratorException.class,
                    () -> configurator.configure(rootMapping, context),
                    "The configurator should throw an exception if the file payload cannot be persisted to disk.");

            assertTrue(
                    thrown.getMessage().contains("Failed to write script file"),
                    "Error should report file persistence failures");
        } finally {
            Files.deleteIfExists(conflictingDir);
        }
    }

    @Test
    public void testConfigureHandlesSaveXmlException(@SuppressWarnings("unused") JenkinsConfiguredWithCodeRule j)
            throws Exception {
        ConfigurationContext context = new ConfigurationContext(ConfiguratorRegistry.get());
        Configurator<ScriptlerConfiguration> configurator = context.lookupOrFail(ScriptlerConfiguration.class);

        Path scriptlerHome = ScriptlerManagement.getScriptDirectory2().getParent();
        Path configFilePath = scriptlerHome.resolve("scriptler.xml");

        Files.deleteIfExists(configFilePath);
        Files.createDirectories(configFilePath);

        try {
            Mapping rootMapping = new Mapping();
            rootMapping.put("disableRemoteCatalog", "true");
            rootMapping.put("scripts", new Sequence());

            ConfiguratorException thrown = assertThrows(
                    ConfiguratorException.class,
                    () -> configurator.configure(rootMapping, context),
                    "The configurator should throw an exception if the global config file cannot be written.");

            assertTrue(
                    thrown.getMessage().contains("Failed to save scriptler configuration file"),
                    "Error should report XML configuration file save persistence failure");
        } finally {
            Files.deleteIfExists(configFilePath);
        }
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
