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
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.scriptler.config.Script;
import org.jenkinsci.plugins.scriptler.config.ScriptlerConfiguration;
import org.jenkinsci.plugins.scriptler.util.ScriptHelper;

/**
 * @author domi
 * 
 */
public class ScriptlerPluginImpl extends Plugin {

    private final static Logger LOGGER = Logger.getLogger(ScriptlerPluginImpl.class.getName());
    
    public static final PermissionGroup SCRIPTLER_PERMISSIONS = new PermissionGroup(ScriptlerManagement.class, Messages._permissons_title());

    public static final Permission CONFIGURE = new Permission(
            SCRIPTLER_PERMISSIONS, "Configure",
            Messages._permissons_configure_description(), Jenkins.RUN_SCRIPTS,
            PermissionScope.JENKINS
    );
    public static final Permission RUN_SCRIPTS = new Permission(
            SCRIPTLER_PERMISSIONS, "RunScripts",
            Messages._permissons_runScript_description(), Jenkins.RUN_SCRIPTS,
            PermissionScope.JENKINS
    );
    
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
        LOGGER.info("initialize Scriptler");
        if (!ScriptlerManagement.getScriptlerHomeDirectory().exists()) {
            boolean dirsDone = ScriptlerManagement.getScriptlerHomeDirectory().mkdirs();
            if(!dirsDone) {
                LOGGER.severe("could not create Scriptler home directory: " + ScriptlerManagement.getScriptlerHomeDirectory());
            }
        }
        File scriptDirectory = ScriptlerManagement.getScriptDirectory();
        // create the directory for the scripts if not available
        if (!scriptDirectory.exists()) {
            scriptDirectory.mkdirs();
        }

        ScriptlerConfiguration cfg = ScriptlerConfiguration.load();

        SyncUtil.syncDirWithCfg(scriptDirectory, cfg);

        cfg.save();
    }

    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void afterJobLoaded() throws Exception {
        setupExistingScripts();
    }
    
    private static void setupExistingScripts() throws Exception {
        for (Script script : ScriptlerConfiguration.getConfiguration().getScripts()) {
            File scriptFile = new File(ScriptlerManagement.getScriptDirectory(), script.getScriptPath());
            try{
                String scriptSource = FileUtils.readFileToString(scriptFile, "UTF-8");
    
                // we cannot do that during start since the ScriptApproval is not yet loaded
                // and only after JOB_LOADED to have the securityRealm configured
                ScriptHelper.putScriptInApprovalQueueIfRequired(scriptSource);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Source file for the script [{0}] was not found", script.getId());
            }
        }
    }
}
