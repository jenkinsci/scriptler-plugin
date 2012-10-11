package org.jenkinsci.plugins.scriptler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.scriptler.config.Script;
import org.jenkinsci.plugins.scriptler.config.ScriptlerConfiguration;

public class SyncUtil {

    private final static Logger LOGGER = Logger.getLogger(SyncUtil.class.getName());

    private SyncUtil() {
    }

    /**
     * 
     * @param repodir
     * @param scriptDirectory
     * @param cfg
     *            must be saved (by caller) after finishing this all sync
     * @throws IOException
     */
    public static void syncDirWithCfg(String repodir, File scriptDirectory, ScriptlerConfiguration cfg) throws IOException {

        List<File> availablePhysicalScripts = getAvailableScripts(scriptDirectory);

        // check if all physical files are available in the configuration
        // if not, add it to the configuration
        for (File file : availablePhysicalScripts) {
            if (cfg.getScriptById(file.getName()) == null) {
                cfg.addOrReplace(new Script(file.getName(), file.getName(), Messages.script_loaded_from_directory(), false, null, false, repodir));
            }
        }

        // check if all scripts in the configuration are physically available
        // if not, mark it as missing
        Set<Script> unavailableScripts = new HashSet<Script>();
        for (Script s : cfg.getScripts()) {
            if (!(new File(scriptDirectory, s.getId()).exists())) {
                unavailableScripts.add(new Script(s.getId(), s.comment, false, false, false));
            }
        }

        for (Script script : unavailableScripts) {
            cfg.addOrReplace(script);
        }
    }

    /**
     * search into the declared backup directory for backup archives
     */
    private static List<File> getAvailableScripts(File scriptDirectory) throws IOException {
        LOGGER.log(Level.FINE, "Listing files of {0}", scriptDirectory.getAbsoluteFile());

        File[] scriptFiles = scriptDirectory.listFiles();

        List<File> fileList;
        if (scriptFiles == null) {
            fileList = new ArrayList<File>();
        } else {
            fileList = Arrays.asList(scriptFiles);
        }

        return fileList;
    }

}
