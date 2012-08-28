/*
 * The MIT License
 *
 * Copyright (c) 2010, Dominik Bartholdi
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

import hudson.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.scriptler.config.Script;
import org.jenkinsci.plugins.scriptler.config.ScriptlerConfiguration;

/**
 * @author domi
 * 
 */
public class ScritplerPluginImpl extends Plugin {

    private final static Logger LOGGER = Logger.getLogger(ScritplerPluginImpl.class.getName());

    @Override
    public void start() throws Exception {
        super.start();
        synchronizeConfig();
    }

    /**
     * Checks if all available scripts on the system are in the config and if all configured files are physically on the filesystem.
     * 
     * @throws IOException
     */
    private void synchronizeConfig() throws IOException {
        LOGGER.info("initialize scriptler");

        if (!ScriptlerManagment.getScriptlerHomeDirectory().exists()) {
            ScriptlerManagment.getScriptlerHomeDirectory().mkdirs();
        }
        File scriptDirectory = ScriptlerManagment.getScriptDirectory();
        // create the directory for the scripts if not available
        if (!scriptDirectory.exists()) {
            scriptDirectory.mkdirs();
        }

        List<File> availablePhysicalScripts = getAvailableScripts();

        ScriptlerConfiguration cfg = ScriptlerConfiguration.load();
        if (cfg == null) {
            cfg = new ScriptlerConfiguration(new TreeSet<Script>());
        }

        // check if all physical files are available in the configuration
        // if not, add it to the configuration
        for (File file : availablePhysicalScripts) {
            if (cfg.getScriptById(file.getName()) == null) {
                cfg.addOrReplace(new Script(file.getName(), file.getName(), Messages.script_loaded_from_directory(), false, null,false));
            }
        }

        // check if all scripts in the configuration are physically available
        // if not, mark it as missing
        Set<Script> unavailableScripts = new HashSet<Script>();
        for (Script s : cfg.getScripts()) {
            if (!(new File(scriptDirectory, s.getId()).exists())) {
                unavailableScripts.add(new Script(s.getId(), s.comment, false, false,false));
            }
        }
        for (Script script : unavailableScripts) {
            cfg.addOrReplace(script);
        }

        cfg.save();
    }

    /**
     * search into the declared backup directory for backup archives
     */
    public List<File> getAvailableScripts() throws IOException {
        File scriptDirectory = ScriptlerManagment.getScriptDirectory();
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
