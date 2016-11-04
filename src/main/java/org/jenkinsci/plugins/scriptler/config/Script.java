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
    public final String originCatalog;
    public final String originScript;
    public final String originDate;
    private Parameter[] parameters;

    public boolean available = true;

    /**
     * script is only transient, because it will not be saved in the xml but on the file system. Therefore it has to be materialized before usage!
     */
    public transient String script;

    /**
     * same as script, with textarea escaped, when displayed in a textarea
     */
    public transient String scriptTextAreaEscaped;
    
    // for user with RUN_SCRIPT permission
    public final boolean nonAdministerUsing;

    // script is runnable only on Master
    public final boolean onlyMaster;

    /**
     * used to create/update a new script in the UI
     */
    @DataBoundConstructor
    public Script(String id, String name, String comment, boolean nonAdministerUsing, Parameter[] parameters, boolean onlyMaster) {
        this(id, name, comment, null, null, null, nonAdministerUsing, parameters, onlyMaster);
    }

    /**
     * used during plugin start to synchronize available scripts
     */
    public Script(String id, String comment, boolean available, boolean nonAdministerUsing, boolean onlyMaster) {
        this(id, id, comment, null, null, null, nonAdministerUsing, new Parameter[0], onlyMaster);
    }

    /**
     * Constructor to create a script imported from a foreign catalog.
     * 
     */
    public Script(String id, String name, String comment, boolean available, String originCatalog, String originScript, String originDate, Parameter[] parameters) {
        this(id, name, comment, originCatalog, originScript, originDate, false, parameters, false);
    }

    /**
     * used to merge scripts
     */
    public Script(String id, String name, String comment, boolean available, String originCatalog, String originScript, String originDate, boolean nonAdministerUsing, Parameter[] parameters, boolean onlyMaster) {
        this(id, name, comment, originCatalog, originScript, originDate, nonAdministerUsing, parameters, onlyMaster);
    }

    /**
     * used to merge scripts
     */
    public Script(String id, String name, String comment, String originCatalog, String originScript, String originDate, boolean nonAdministerUsing, Parameter[] parameters, boolean onlyMaster) {
        this.id = id;
        this.name = name;
        this.comment = comment;
        this.originCatalog = originCatalog;
        this.originScript = originScript;
        this.originDate = originDate;
        this.nonAdministerUsing = nonAdministerUsing;
        this.parameters = parameters;
        this.onlyMaster = onlyMaster;
    }

    public Script copy() {
        return new Script(id, name, comment, originCatalog, originScript, originDate, nonAdministerUsing, parameters, onlyMaster);
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
    
    /**
     * used to escape textarea when script is displayed in a textarea
     */
    public void computeTextAreaEscaped() {
    	if(script!=null)this.scriptTextAreaEscaped = script.replaceAll("</textarea", "&lt;/textarea");
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
