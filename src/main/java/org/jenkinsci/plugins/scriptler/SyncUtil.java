package org.jenkinsci.plugins.scriptler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.jenkinsci.plugins.scriptler.config.Script;
import org.jenkinsci.plugins.scriptler.config.ScriptlerConfiguration;
import org.jenkinsci.plugins.scriptler.share.ScriptInfo;
import org.jenkinsci.plugins.scriptler.util.ScriptHelper;

public class SyncUtil {

    private static final Logger LOGGER = Logger.getLogger(SyncUtil.class.getName());

    private SyncUtil() {}

    /**
     * @deprecated Use {@link #syncDirWithCfg(Path, ScriptlerConfiguration)} instead.
     */
    @Deprecated(since = "380")
    public static void syncDirWithCfg(File scriptDirectory, ScriptlerConfiguration cfg) throws IOException {
        syncDirWithCfg(scriptDirectory.toPath(), cfg);
    }

    /**
     *
     * @param scriptDirectory
     * @param cfg
     *            must be saved (by caller) after finishing this all sync
     * @throws IOException
     */
    public static void syncDirWithCfg(Path scriptDirectory, ScriptlerConfiguration cfg) throws IOException {

        List<Path> availablePhysicalScripts = getAvailableScripts(scriptDirectory);

        // check if all physical files are available in the configuration
        // if not, add it to the configuration
        for (Path file : availablePhysicalScripts) {
            final String fileName = Objects.toString(file.getFileName(), null);
            if (cfg.getScriptById(fileName) == null) {
                final ScriptInfo info = ScriptHelper.extractScriptInfo(ScriptHelper.readScriptFromFile(file));
                if (info != null) {
                    List<Parameter> parameters = info.getParameters().stream()
                            .map(name -> new Parameter(name, null))
                            .collect(Collectors.toList());
                    cfg.addOrReplace(new Script(fileName, info.getName(), info.getComment(), false, parameters, false));
                } else {
                    cfg.addOrReplace(new Script(
                            fileName,
                            fileName,
                            Messages.script_loaded_from_directory(),
                            false,
                            Collections.emptyList(),
                            false));
                }
            }
        }

        // check if all scripts in the configuration are physically available
        // if not, mark it as missing
        Set<Script> unavailableScripts = new HashSet<>();
        for (Script s : cfg.getScripts()) {
            // only check the scripts belonging to this repodir
            if (Files.exists(scriptDirectory.resolve(s.getScriptPath()))) {
                s.setAvailable(true);
            } else {
                Script unavailableScript = new Script(s.getId(), s.comment, false, false, false);
                // to no loose parameter configuration if we loose the file
                unavailableScript.setParameters(s.getParameters());
                unavailableScripts.add(unavailableScript);
                LOGGER.info("for repo '" + scriptDirectory + "' " + s + " is not available!");
            }
        }

        for (Script script : unavailableScripts) {
            cfg.addOrReplace(script);
        }
    }

    /**
     * search into the declared backup directory for backup archives
     */
    private static List<Path> getAvailableScripts(Path scriptDirectory) throws IOException {
        LOGGER.log(Level.FINE, "Listing files of {0}", scriptDirectory);

        try (Stream<Path> contents = Files.list(scriptDirectory)) {
            return contents.filter(path -> path.endsWith(".groovy")).toList();
        }
    }
}
