package org.jenkinsci.plugins.scriptler;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
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
                final ScriptInfo info = ScriptHelper.extractScriptInfo(FileUtils.readFileToString(file, "UTF-8"));
                if (info != null) {
                    final List<String> paramList = info.getParameters();
                    Parameter[] parameters = new Parameter[paramList == null ? 0 : paramList.size()];
                    for (int i = 0; i < parameters.length; i++) {
                        parameters[i] = new Parameter(paramList.get(i), null);
                    }
                    cfg.addOrReplace(new Script(file.getName(), info.getName(), info.getComment(), false, parameters, false, repodir));
                } else {
                    cfg.addOrReplace(new Script(file.getName(), file.getName(), Messages.script_loaded_from_directory(), false, null, false, repodir));
                }

            }
        }

        // check if all scripts in the configuration are physically available
        // if not, mark it as missing
        Set<Script> unavailableScripts = new HashSet<Script>();
        for (Script s : cfg.getScriptsForRepodir(repodir)) {
            // only check the scripts belonging to this repodir
            if ((new File(s.getScriptPath()).exists())) {
                s.setAvailable(true);
            } else {
                unavailableScripts.add(new Script(s.getId(), s.comment, false, false, false, repodir));
                LOGGER.info("for repo '" + repodir + "' " + s + " is not available!");
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

        File[] scriptFiles = scriptDirectory.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".groovy");
            }
        });

        List<File> fileList;
        if (scriptFiles == null) {
            fileList = new ArrayList<File>();
        } else {
            fileList = Arrays.asList(scriptFiles);
        }

        return fileList;
    }

}
