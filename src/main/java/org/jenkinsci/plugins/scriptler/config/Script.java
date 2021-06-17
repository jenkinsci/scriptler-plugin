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

import java.util.Comparator;
import java.util.Objects;

public class Script implements Comparable<Script>, NamedResource {

    private final String id;
    public final String name;
    public final String comment;
    public final String originCatalog;
    public final String originScript;
    public final String originDate;
    private Parameter[] parameters;

    public boolean available = true;

    /**
     * script is only transient, because it will not be saved in the xml but on the file system. Therefore it has to be materialized before usage!
     */
    public transient String script;

    // User with Scriptler/RUN_SCRIPT permission can add/edit Scriptler step in projects
    public final boolean nonAdministerUsing;

    // script is runnable only on Master
    public final boolean onlyMaster;

    /**
     * used to create/update a new script in the UI
     */
    public Script(String id, String name, String comment, boolean nonAdministerUsing, Parameter[] parameters, boolean onlyMaster) {
        this(id, name, comment, true, null, null, null, nonAdministerUsing, parameters, onlyMaster);
    }

    /**
     * used during plugin start to synchronize available scripts
     */
    public Script(String id, String comment, boolean available, boolean nonAdministerUsing, boolean onlyMaster) {
        this(id, id, comment, available, null, null, null, nonAdministerUsing, new Parameter[0], onlyMaster);
    }

    /**
     * Constructor to create a script imported from a foreign catalog.
     * 
     */
    public Script(String id, String name, String comment, boolean available, String originCatalog, String originScript, String originDate, Parameter[] parameters) {
        this(id, name, comment, available, originCatalog, originScript, originDate, false, parameters, false);
    }

    // Not used anymore
    /**
     * used to merge scripts
     */
    public Script(String id, String name, String comment, String originCatalog, String originScript, String originDate, boolean nonAdministerUsing, Parameter[] parameters, boolean onlyMaster) {
        this(id, name, comment, true, originCatalog, originScript, originDate, nonAdministerUsing, parameters, onlyMaster);
    }
    
    /**
     * used to merge scripts
     */
    public Script(String id, String name, String comment, boolean available, String originCatalog, String originScript, String originDate, boolean nonAdministerUsing, Parameter[] parameters, boolean onlyMaster) {
        this.id = id;
        this.name = name;
        this.comment = comment;
        this.available = available;
        this.originCatalog = originCatalog;
        this.originScript = originScript;
        this.originDate = originDate;
        this.nonAdministerUsing = nonAdministerUsing;
        this.parameters = parameters;
        this.onlyMaster = onlyMaster;
    }

    public Script copy() {
        return new Script(id, name, comment, available, originCatalog, originScript, originDate, nonAdministerUsing, parameters, onlyMaster);
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see org.jvnet.hudson.plugins.scriptler.config.NamedResource#getName()
     */
    public String getName() {
        return name;
    }

    public String getScriptPath() {
        return id;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public void setParameters(Parameter[] parameters) {
        this.parameters = parameters;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Script o) {
        return id.compareTo(o.id);
    }

    /**
     * Previously we used not to have an id, but only a name.
     */
    public Object readResolve() {
        if (id == null) {
            return new Script(name, name, comment, available, originCatalog, originScript, originDate, nonAdministerUsing, parameters, onlyMaster);
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

    public static final Comparator<Script> COMPARATOR_BY_NAME = new Comparator<Script>() {
        @Override public int compare(Script a, Script b) {
            String nameA = a.getName() != null ? a.getName() : "";
            String nameB = b.getName() != null ? b.getName() : "";
            return nameA.compareToIgnoreCase(nameB);
        }
    };
}
