package org.jenkinsci.plugins.scriptler;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.jenkinsci.plugins.scriptler.config.Script;
import org.jenkinsci.plugins.scriptler.config.ScriptlerConfiguration;
import org.jenkinsci.plugins.scriptler.share.ScriptInfo;
import org.jenkinsci.plugins.scriptler.util.ScriptHelper;

public class SyncUtil {

    private final static Logger LOGGER = Logger.getLogger(SyncUtil.class.getName());

    private SyncUtil() {
    }

    /**
     * 
     * @param scriptDirectory
     * @param cfg
     *            must be saved (by caller) after finishing this all sync
     * @throws IOException
     */
    public static void syncDirWithCfg(File scriptDirectory, ScriptlerConfiguration cfg) throws IOException {

        List<File> availablePhysicalScripts = getAvailableScripts(scriptDirectory);

        // check if all physical files are available in the configuration
        // if not, add it to the configuration
        for (File file : availablePhysicalScripts) {
            if (cfg.getScriptById(file.getName()) == null) {
                final ScriptInfo info = ScriptHelper.extractScriptInfo(ScriptHelper.readScriptFromFile(file));
                if (info != null) {
                    List<Parameter> parameters = info.getParameters().stream()
                            .map(name -> new Parameter(name, null))
                            .collect(Collectors.toList());
                    cfg.addOrReplace(new Script(file.getName(), info.getName(), info.getComment(), false, parameters, false));
                } else {
                    cfg.addOrReplace(new Script(file.getName(), file.getName(), Messages.script_loaded_from_directory(), false, Collections.emptyList(), false));
                }

            }
        }

        // check if all scripts in the configuration are physically available
        // if not, mark it as missing
        Set<Script> unavailableScripts = new HashSet<>();
        for (Script s : cfg.getScripts()) {
            // only check the scripts belonging to this repodir
            if ((new File(scriptDirectory, s.getScriptPath()).exists())) {
                s.setAvailable(true);
            } else {
                Script unavailableScript = new Script(s.getId(), s.comment, false, false, false);
                // to no loose parameter configuration if we loose the file
                unavailableScript.setParameters(s.getParameters());
                unavailableScripts.add(unavailableScript);
                LOGGER.info("for repo '" + scriptDirectory.getAbsolutePath() + "' " + s + " is not available!");
            }
        }

        for (Script script : unavailableScripts) {
            cfg.addOrReplace(script);
        }
    }

    /**
     * search into the declared backup directory for backup archives
     */
    private static List<File> getAvailableScripts(File scriptDirectory) {
        LOGGER.log(Level.FINE, "Listing files of {0}", scriptDirectory.getAbsoluteFile());

        File[] scriptFiles = scriptDirectory.listFiles((File dir, String name) -> name.endsWith(".groovy"));

        List<File> fileList;
        if (scriptFiles == null) {
            fileList = new ArrayList<>();
        } else {
            fileList = Arrays.asList(scriptFiles);
        }

        return fileList;
    }

}
