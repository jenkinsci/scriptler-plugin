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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;

public class Script implements Comparable<Script>, NamedResource, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    public final String name;
    public final String comment;
    public final String originCatalog;
    public final String originScript;
    public final String originDate;

    @NonNull
    private final List<Parameter> parameters;

    private boolean available;

    /**
     * script is only transient, because it will not be saved in the xml but on the file system. Therefore it has to be materialized before usage!
     */
    private transient String scriptText;

    /**
     * @deprecated Use {@link #getScriptText()} and {@link #setScriptText(String)} instead.
     */
    @Deprecated(since = "384")
    public transient String script;

    // User with Scriptler/RUN_SCRIPT permission can add/edit Scriptler step in projects
    public final boolean nonAdministerUsing;

    // script is runnable only on the built-in node
    public final boolean onlyBuiltIn;

    /**
     * @deprecated Use {@link #onlyBuiltIn} instead.
     */
    @Deprecated(since = "386")
    public final Boolean onlyController;

    /**
     * @deprecated Use {@link #onlyBuiltIn} instead.
     */
    @Deprecated(since = "381")
    public final Boolean onlyMaster;

    /**
     * used to create/update a new script in the UI
     */
    public Script(
            String id,
            String name,
            String comment,
            boolean nonAdministerUsing,
            @NonNull List<Parameter> parameters,
            boolean onlyBuiltIn) {
        this(id, name, comment, true, null, null, null, nonAdministerUsing, parameters, onlyBuiltIn);
    }

    /**
     * used during plugin start to synchronize available scripts
     */
    public Script(String id, String comment, boolean available, boolean nonAdministerUsing, boolean onlyBuiltIn) {
        this(id, id, comment, available, null, null, null, nonAdministerUsing, List.of(), onlyBuiltIn);
    }

    /**
     * Constructor to create a script imported from a foreign catalog.
     *
     */
    public Script(
            String id,
            String name,
            String comment,
            boolean available,
            String originCatalog,
            String originScript,
            String originDate,
            @NonNull List<Parameter> parameters) {
        this(id, name, comment, available, originCatalog, originScript, originDate, false, parameters, false);
    }

    // Not used anymore
    /**
     * used to merge scripts
     */
    public Script(
            String id,
            String name,
            String comment,
            String originCatalog,
            String originScript,
            String originDate,
            boolean nonAdministerUsing,
            @NonNull List<Parameter> parameters,
            boolean onlyBuiltIn) {
        this(
                id,
                name,
                comment,
                true,
                originCatalog,
                originScript,
                originDate,
                nonAdministerUsing,
                parameters,
                onlyBuiltIn);
    }

    /**
     * used to merge scripts
     */
    public Script(
            String id,
            String name,
            String comment,
            boolean available,
            String originCatalog,
            String originScript,
            String originDate,
            boolean nonAdministerUsing,
            @NonNull List<Parameter> parameters,
            boolean onlyBuiltIn) {
        this.id = id;
        this.name = name;
        this.comment = comment;
        this.available = available;
        this.originCatalog = originCatalog;
        this.originScript = originScript;
        this.originDate = originDate;
        this.nonAdministerUsing = nonAdministerUsing;
        this.parameters = new ArrayList<>(parameters);
        this.onlyBuiltIn = onlyBuiltIn;
        this.onlyMaster = this.onlyController = null;
    }

    public Script copy() {
        return new Script(
                id,
                name,
                comment,
                available,
                originCatalog,
                originScript,
                originDate,
                nonAdministerUsing,
                parameters,
                onlyBuiltIn);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.jvnet.hudson.plugins.scriptler.config.NamedResource#getName()
     */
    public String getName() {
        return name;
    }

    public String getNonNullName() {
        return Util.fixNull(name);
    }

    public String getScriptPath() {
        return id;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @CheckForNull
    public String getScriptText() {
        return scriptText;
    }

    /**
     * @deprecated Use {@link #getScriptText()} instead.
     */
    @CheckForNull
    @Deprecated(since = "381")
    public String getScript() {
        return getScriptText();
    }

    @SuppressFBWarnings("PA_PUBLIC_PRIMITIVE_ATTRIBUTE")
    @SuppressWarnings({"deprecated", "java:S1874"})
    public void setScriptText(String scriptText) {
        this.scriptText = scriptText;
        script = scriptText;
    }

    /**
     * @deprecated Use {@link #setScriptText(String)} instead.
     */
    @Deprecated(since = "381")
    public void setScript(String scriptText) {
        setScriptText(scriptText);
    }

    public void setParameters(@NonNull List<Parameter> parameters) {
        this.parameters.clear();
        this.parameters.addAll(parameters);
    }

    @NonNull
    public List<Parameter> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Script o) {
        return id.compareTo(o.id);
    }

    @Serial
    public Object readResolve() {
        if (onlyMaster != null || onlyController != null) {
            boolean onlyBuiltIn = onlyMaster == null ? onlyController : onlyMaster;
            return new Script(
                    id,
                    name,
                    comment,
                    available,
                    originCatalog,
                    originScript,
                    originDate,
                    nonAdministerUsing,
                    parameters,
                    onlyBuiltIn);
        }
        return this;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    public String toString() {
        return "[Script: " + id + ":" + name + "]";
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Script other = (Script) obj;
        return Objects.equals(id, other.id);
    }

    public static final Comparator<Script> COMPARATOR_BY_NAME =
            Comparator.comparing(Script::getNonNullName, String.CASE_INSENSITIVE_ORDER);
}
