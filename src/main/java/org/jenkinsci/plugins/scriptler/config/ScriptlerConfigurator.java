package org.jenkinsci.plugins.scriptler.config;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.Util;
import io.jenkins.plugins.casc.Attribute;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.Configurator;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.RootElementConfigurator;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import io.jenkins.plugins.casc.model.Sequence;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.scriptler.ScriptlerManagement;
import org.jenkinsci.plugins.scriptler.git.GitScriptlerRepository;
import org.jenkinsci.plugins.scriptler.util.ScriptHelper;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.GroovyLanguage;
import org.jenkinsci.plugins.variant.OptionalExtension;

@OptionalExtension(requirePlugins = "configuration-as-code")
public class ScriptlerConfigurator implements RootElementConfigurator<ScriptlerConfiguration> {

    private static final Logger LOGGER = Logger.getLogger(ScriptlerConfigurator.class.getName());

    @Override
    @NonNull
    public String getName() {
        return "scriptler";
    }

    @Override
    public ScriptlerConfiguration getTargetComponent(ConfigurationContext context) {
        return ScriptlerConfiguration.getConfiguration();
    }

    @Override
    public Class<ScriptlerConfiguration> getTarget() {
        return ScriptlerConfiguration.class;
    }

    @Override
    @NonNull
    public Set<Attribute<ScriptlerConfiguration, ?>> describe() {
        Set<Attribute<ScriptlerConfiguration, ?>> attributes = new TreeSet<>(Comparator.comparing(Attribute::getName));
        attributes.add(new Attribute<ScriptlerConfiguration, Boolean>("disableRemoteCatalog", Boolean.class));
        attributes.add(new Attribute<ScriptlerConfiguration, Script>("scripts", Script.class).multiple(true));
        return attributes;
    }

    @Override
    @NonNull
    public ScriptlerConfiguration check(CNode config, ConfigurationContext context) throws ConfiguratorException {
        Mapping mapping = config.asMapping();

        if (mapping.containsKey("scripts")) {
            Sequence sequence;
            try {
                sequence = mapping.get("scripts").asSequence();
            } catch (Exception e) {
                throw new ConfiguratorException(this, "The 'scripts' attribute must be a sequence (list).", e);
            }

            Configurator<Script> scriptConfigurator = context.lookupOrFail(Script.class);

            for (CNode node : sequence) {
                scriptConfigurator.check(node, context);
            }
        }
        return ScriptlerConfiguration.getConfiguration();
    }

    @Override
    @NonNull
    public ScriptlerConfiguration configure(CNode config, ConfigurationContext context) throws ConfiguratorException {
        Mapping mapping = config.asMapping();
        ScriptlerConfiguration cfg = ScriptlerConfiguration.getConfiguration();

        if (mapping.containsKey("disableRemoteCatalog")) {
            try {
                String boolVal = mapping.get("disableRemoteCatalog").asScalar().getValue();
                cfg.setDisableRemoteCatalog(Boolean.parseBoolean(boolVal));
            } catch (Exception e) {
                throw new ConfiguratorException(this, "Failed to parse disableRemoteCatalog attribute", e);
            }
        }

        if (mapping.containsKey("scripts")) {
            Sequence sequence = mapping.get("scripts").asSequence();
            SortedSet<Script> newScriptsSet = new TreeSet<>();

            Configurator<Script> scriptConfigurator = context.lookupOrFail(Script.class);
            for (CNode node : sequence) {
                Script script = scriptConfigurator.configure(node, context);
                String id = script.getId();
                if (id == null || id.isEmpty()) {
                    continue;
                }

                Mapping scriptMap = node.asMapping();
                if (scriptMap.containsKey("scriptText")) {
                    String scriptText = scriptMap.get("scriptText").asScalar().getValue();
                    script.setScriptText(scriptText);
                }

                String fixedFileName = id.endsWith(".groovy") ? id : id + ".groovy";
                fixedFileName = fixedFileName.replace(" ", "_").trim();

                try {
                    Path scriptDirectory = ScriptlerManagement.getScriptDirectory2();
                    Path newScriptFile = scriptDirectory.resolve(fixedFileName);

                    if (!Util.isDescendant(scriptDirectory.toFile(), newScriptFile.toFile())) {
                        LOGGER.log(
                                Level.WARNING,
                                "Folder traversal detected in JCasC configuration for script ID: {0}",
                                id);
                        throw new ConfiguratorException(this, "Folder traversal detected in script ID: " + id);
                    }

                    String text = script.getScriptText();
                    if (text != null) {
                        ScriptHelper.writeScriptToFile(newScriptFile, text);
                        ScriptApproval scriptApproval = ScriptApproval.get();
                        scriptApproval.configuring(
                                text,
                                GroovyLanguage.get(),
                                ApprovalContext.create().withUser("JCasC"),
                                true);

                        ExtensionList<GitScriptlerRepository> gitRepos =
                                ExtensionList.lookup(GitScriptlerRepository.class);
                        if (!gitRepos.isEmpty()) {
                            try {
                                gitRepos.get(0).addSingleFileToRepo(fixedFileName);
                            } catch (Exception gitEx) {
                                LOGGER.log(Level.WARNING, "Failed to add script to Git repository: " + id, gitEx);
                            }
                        }
                    }
                    newScriptsSet.add(script);
                } catch (ConfiguratorException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ConfiguratorException(this, "Failed to write script file for " + id, e);
                }
            }
            cfg.setScripts(newScriptsSet);
        }

        try {
            cfg.save();
        } catch (IOException e) {
            throw new ConfiguratorException(this, "Failed to save scriptler configuration file", e);
        }

        return cfg;
    }

    @Override
    public CNode describe(ScriptlerConfiguration instance, ConfigurationContext context) throws Exception {
        Mapping mapping = new Mapping();
        mapping.put("disableRemoteCatalog", instance.isDisableRemoteCatalog());

        Sequence sequence = new Sequence();

        Configurator<Script> rawConfigurator = context.lookupOrFail(Script.class);

        for (Script s : instance.getScripts()) {
            Script copied = s.copy();
            Script scriptWithText = ScriptHelper.getScript(s.getId(), true);
            if (scriptWithText != null) {
                copied.setScriptText(scriptWithText.getScriptText());
            }

            sequence.add(rawConfigurator.describe(copied, context));
        }

        mapping.put("scripts", sequence);
        return mapping;
    }
}
