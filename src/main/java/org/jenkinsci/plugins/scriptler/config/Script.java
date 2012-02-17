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

import org.kohsuke.stapler.DataBoundConstructor;

public class Script implements Comparable<Script>, NamedResource {

    private String id;
    public final String name;
    public final String comment;
    public final boolean available;
    public final String originCatalog;
    public final String originScript;
    public final String originDate;
    private Parameter[] parameters;

    /**
     * script is only transient, because it will not be saved in the xml but on the file system. Therefore it has to be materialized before usage!
     */
    public transient String script;

    // for user with RUN_SCRIPT permission
    public boolean nonAdministerUsing;

    /**
     * used to create/update a new script in the UI
     */
    @DataBoundConstructor
    public Script(String id, String name, String comment, boolean nonAdministerUsing, Parameter[] parameters) {
        this.id = id;
        this.name = name;
        this.comment = comment;
        this.available = true;
        this.originCatalog = null;
        this.originScript = null;
        this.originDate = null;
        this.nonAdministerUsing = nonAdministerUsing;
        this.parameters = parameters;
    }

    /**
     * used during upload of a new script
     */
    public Script(String id, String comment) {
        this.id = id;
        this.name = id;
        this.comment = comment;
        this.available = true;
        this.originCatalog = null;
        this.originScript = null;
        this.originDate = null;
        this.nonAdministerUsing = false;
        this.parameters = null;
    }

    /**
     * used during plugin start to synchronize available scripts
     */
    public Script(String id, String comment, boolean available, boolean nonAdministerUsing) {
        this.id = id;
        this.name = id;
        this.comment = comment;
        this.available = available;
        this.originCatalog = null;
        this.originScript = null;
        this.originDate = null;
        this.nonAdministerUsing = nonAdministerUsing;
        this.parameters = null;
    }

    /**
     * Constructor to create a script imported from a foreign catalog.
     */
    public Script(String id, String name, String comment, boolean available, String originCatalog, String originScript, String originDate,
            Parameter[] parameters) {
        this.id = id;
        this.name = name;
        this.comment = comment;
        this.available = available;
        this.originCatalog = originCatalog;
        this.originScript = originScript;
        this.originDate = originDate;
        this.nonAdministerUsing = false;
        this.parameters = parameters;

    }

    /**
     * used to merge scripts
     */
    public Script(String id, String name, String comment, boolean available, String originCatalog, String originScript, String originDate,
            boolean nonAdministerUsing, Parameter[] parameters) {
        this.id = id;
        this.name = name;
        this.comment = comment;
        this.available = available;
        this.originCatalog = originCatalog;
        this.originScript = originScript;
        this.originDate = originDate;
        this.nonAdministerUsing = nonAdministerUsing;
        this.parameters = parameters;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jvnet.hudson.plugins.scriptler.config.NamedResource#getName()
     */
    public String getName() {
        return name;
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

    public void setNonAdministerUsing(boolean nonAdministerUsing) {
        this.nonAdministerUsing = nonAdministerUsing;
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
            id = name;
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
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Script other = (Script) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        return true;
    }

}
