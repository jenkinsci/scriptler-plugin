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
package org.jenkinsci.plugins.scriptler.config;

import hudson.BulkChange;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.util.XStream2;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import org.jenkinsci.plugins.scriptler.ScriptlerManagement;
import org.jenkinsci.plugins.scriptler.ScriptlerPluginImpl;
import org.jenkinsci.plugins.scriptler.share.CatalogInfo;
import org.jenkinsci.plugins.scriptler.util.ByIdSorter;

import com.thoughtworks.xstream.XStream;
import org.jenkinsci.plugins.scriptler.util.ScriptHelper;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;

/**
 */
public final class ScriptlerConfiguration extends ScriptSet implements Saveable {

    private final static Logger LOGGER = Logger.getLogger(ScriptlerConfiguration.class.getName());

    // keep to avoid loading issues with older version
    @Deprecated
    private transient List<CatalogInfo> catalogInfos = new ArrayList<CatalogInfo>();

    private boolean disbableRemoteCatalog = false;

    /**
     * /!\ keep to avoid loading issues with older version
     * The regular permission required is Scriptler/RunScripts now
     * @deprecated no need to replace them, Script Security is used now
     */
    @Deprecated
    private boolean allowRunScriptPermission = false;
    
    /**
     * /!\ keep to avoid loading issues with older version
     * The regular permission required is Scriptler/Configure now
     * @deprecated no need to replace them, Script Security is used now
     */
    @Deprecated
    private boolean allowRunScriptEdit = false;

    public ScriptlerConfiguration(SortedSet<Script> scripts) {
        if (scripts != null) {
            this.scriptSet = scripts;
        }
    }

    public synchronized void save() throws IOException {
        if (BulkChange.contains(this))
            return;
        getXmlFile().write(this);
        SaveableListener.fireOnChange(this, getXmlFile());
    }

    public static XmlFile getXmlFile() {
        return new XmlFile(XSTREAM, new File(ScriptlerManagement.getScriptlerHomeDirectory(), "scriptler.xml"));
    }

    public static @Nonnull ScriptlerConfiguration load() throws IOException {
        XmlFile f = getXmlFile();
        if (f.exists()) {
            // As it might be that we have an unsorted set, we ensure the
            // sorting at load time.
            ScriptlerConfiguration sc = (ScriptlerConfiguration) f.read();
            SortedSet<Script> sorted = new TreeSet<Script>(new ByIdSorter());
            sorted.addAll(sc.getScripts());
            sc.setScripts(sorted);
            return sc;
        } else {
            return new ScriptlerConfiguration(new TreeSet<Script>(new ByIdSorter()));
        }
    }

    // always retrieve via getter
    private static transient volatile ScriptlerConfiguration cfg = null;

    public static ScriptlerConfiguration getConfiguration() {
        if (cfg == null) {
            try {
                cfg = ScriptlerConfiguration.load();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to load scriptler configuration", e);
            }
        }
        return cfg;
    }

    private static final XStream XSTREAM = new XStream2();

    static {
        XSTREAM.alias("scriptler", ScriptlerConfiguration.class);
        XSTREAM.alias("script", Script.class);
        XSTREAM.alias("catalog", CatalogInfo.class);
        XSTREAM.alias("parameter", Parameter.class);
        XSTREAM.alias("org.jvnet.hudson.plugins.scriptler.util.ByNameSorter", ByIdSorter.class);
    }

    public boolean isDisbableRemoteCatalog() {
        return disbableRemoteCatalog;
    }

    public void setDisbableRemoteCatalog(boolean disbableRemoteCatalog) {
        this.disbableRemoteCatalog = disbableRemoteCatalog;
    }

    public void setAllowRunScriptEdit(boolean allowRunScriptEdit) {
        this.allowRunScriptEdit = allowRunScriptEdit;
    }

    public void setAllowRunScriptPermission(boolean allowRunScriptPermission) {
        this.allowRunScriptPermission = allowRunScriptPermission;
    }

    public boolean isAllowRunScriptEdit() {
        return allowRunScriptEdit;
    }

    public boolean isAllowRunScriptPermission() {
        return allowRunScriptPermission;
    }

    @Restricted(DoNotUse.class) // for Jelly view
    public List<ScriptAndApproved> getSortedScripts(){
        List<Script> sortedScripts;
        if(Jenkins.get().hasPermission(ScriptlerPluginImpl.CONFIGURE)){
            sortedScripts = new ArrayList<Script>(this.getScripts());
        }else{
            sortedScripts = new ArrayList<Script>(this.getUserScripts());
        }

        Collections.sort(sortedScripts, Script.COMPARATOR_BY_NAME);

        List<ScriptAndApproved> result = new ArrayList<ScriptAndApproved>(sortedScripts.size());
        for (Script script : sortedScripts) {
            Script scriptWithSrc = ScriptHelper.getScript(script.getId(), true);
            Boolean approved = null;
            if(scriptWithSrc != null && scriptWithSrc.script != null){
                approved = ScriptHelper.isApproved(scriptWithSrc.script, false);
            }
            result.add(new ScriptAndApproved(script, approved));
        }
        return result;
    }
    
    @Restricted(NoExternalUse.class) // for Jelly view
    public static class ScriptAndApproved {
        private Script script;
        private Boolean approved;
    
        private ScriptAndApproved(Script script, Boolean approved) {
            this.script = script;
            this.approved = approved;
        }
    
        public Script getScript() {
            return script;
        }
    
        public Boolean getApproved() {
            return approved;
        }
    }
}
