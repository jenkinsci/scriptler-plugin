/*
 * The MIT License
 *
 * Copyright (c) 2010-2021, Dominik Bartholdi & Michael Tughan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.scriptler;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.scriptler.config.Script;
import org.jenkinsci.plugins.scriptler.config.ScriptlerConfiguration;
import org.jenkinsci.plugins.scriptler.util.ScriptHelper;

public final class ScriptlerLoadingTasks {

    private static final Logger LOGGER = Logger.getLogger(ScriptlerLoadingTasks.class.getName());

    private ScriptlerLoadingTasks() {}

    /**
     * Checks if all available scripts on the system are in the config and if all configured files are physically on the filesystem.
     *
     * @throws IOException if the configuration could not be loaded or saved
     */
    @Initializer(after = InitMilestone.PLUGINS_PREPARED)
    public static void synchronizeConfig() throws IOException {
        LOGGER.info("initialize Scriptler");
        File homeDirectory = ScriptlerManagement.getScriptlerHomeDirectory();
        if (!homeDirectory.exists()) {
            boolean dirsDone = homeDirectory.mkdirs();
            if (!dirsDone) {
                LOGGER.log(Level.SEVERE, "could not create Scriptler home directory: {0}", homeDirectory);
            }
        }

        File scriptDirectory = ScriptlerManagement.getScriptDirectory();
        if (!scriptDirectory.exists() && !scriptDirectory.mkdirs()) {
            LOGGER.log(Level.SEVERE, "could not create Scriptler scripts directory: {0}", scriptDirectory);
        }

        ScriptlerConfiguration cfg = ScriptlerConfiguration.load();

        SyncUtil.syncDirWithCfg(scriptDirectory, cfg);

        cfg.save();
    }

    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void setupExistingScripts() {
        for (Script script : ScriptlerConfiguration.getConfiguration().getScripts()) {
            File scriptFile = new File(ScriptlerManagement.getScriptDirectory(), script.getScriptPath());
            try {
                String scriptSource = ScriptHelper.readScriptFromFile(scriptFile);

                // we cannot do that during start since the ScriptApproval is not yet loaded
                // and only after JOB_LOADED to have the securityRealm configured
                ScriptHelper.putScriptInApprovalQueueIfRequired(scriptSource);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Source file for the script [{0}] was not found", script.getId());
            }
        }
    }
}
