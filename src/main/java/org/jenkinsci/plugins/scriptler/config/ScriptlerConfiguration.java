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

import com.thoughtworks.xstream.XStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.util.XStream2;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.scriptler.ScriptlerManagement;
import org.jenkinsci.plugins.scriptler.ScriptlerPermissions;
import org.jenkinsci.plugins.scriptler.share.CatalogInfo;
import org.jenkinsci.plugins.scriptler.util.ByIdSorter;
import org.jenkinsci.plugins.scriptler.util.ScriptHelper;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;

public final class ScriptlerConfiguration extends ScriptSet implements Saveable, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(ScriptlerConfiguration.class.getName());

    /**
     * @deprecated Use {@link #disableRemoteCatalog} instead.
     */
    @Deprecated(since = "381")
    private Boolean disbableRemoteCatalog = false;

    private boolean disableRemoteCatalog = false;

    public ScriptlerConfiguration(SortedSet<Script> scripts) {
        if (scripts != null) {
            setScripts(scripts);
        }
    }

    @Serial
    @SuppressWarnings({"deprecated", "java:S1874"})
    private Object readResolve() {
        if (disbableRemoteCatalog != null) {
            disableRemoteCatalog = disbableRemoteCatalog;
            disbableRemoteCatalog = null;
        }
        return this;
    }

    public synchronized void save() throws IOException {
        if (BulkChange.contains(this)) return;
        getXmlFile().write(this);
        SaveableListener.fireOnChange(this, getXmlFile());
    }

    public static XmlFile getXmlFile() {
        return new XmlFile(
                XSTREAM,
                ScriptlerManagement.getScriptlerHomeDirectory2()
                        .resolve("scriptler.xml")
                        .toFile());
    }

    public static @NonNull ScriptlerConfiguration load() throws IOException {
        XmlFile f = getXmlFile();
        if (f.exists()) {
            // As it might be that we have an unsorted set, we ensure the
            // sorting at load time.
            ScriptlerConfiguration sc = (ScriptlerConfiguration) f.read();
            SortedSet<Script> sorted = new TreeSet<>(sc.getScripts());
            sc.setScripts(sorted);
            return sc;
        } else {
            return new ScriptlerConfiguration(new TreeSet<>());
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

    public boolean isDisableRemoteCatalog() {
        return disableRemoteCatalog;
    }

    public void setDisableRemoteCatalog(boolean disableRemoteCatalog) {
        this.disableRemoteCatalog = disableRemoteCatalog;
    }

    /**
     * @deprecated Use {@link #isDisableRemoteCatalog()} instead.
     */
    @Deprecated(since = "381")
    public boolean isDisbableRemoteCatalog() {
        return isDisableRemoteCatalog();
    }

    /**
     * @deprecated Use {@link #setDisableRemoteCatalog(boolean)} instead.
     */
    @Deprecated(since = "381")
    public void setDisbableRemoteCatalog(boolean disableRemoteCatalog) {
        setDisableRemoteCatalog(disableRemoteCatalog);
    }

    @Restricted(DoNotUse.class) // for Jelly view
    public List<ScriptAndApproved> getSortedScripts() {
        List<Script> sortedScripts;
        if (Jenkins.get().hasPermission(ScriptlerPermissions.CONFIGURE)) {
            sortedScripts = new ArrayList<>(this.getScripts());
        } else {
            sortedScripts = new ArrayList<>(this.getUserScripts());
        }

        sortedScripts.sort(Script.COMPARATOR_BY_NAME);

        List<ScriptAndApproved> result = new ArrayList<>(sortedScripts.size());
        for (Script script : sortedScripts) {
            Script scriptWithSrc = ScriptHelper.getScript(script.getId(), true);
            Boolean approved = null;
            if (scriptWithSrc != null && scriptWithSrc.getScriptText() != null) {
                approved = ScriptHelper.isApproved(scriptWithSrc.getScriptText(), false);
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
