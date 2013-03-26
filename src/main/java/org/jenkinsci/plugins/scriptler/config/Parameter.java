package org.jenkinsci.plugins.scriptler.config;

import java.io.Serializable;

public class Parameter implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private Object value;

    public Parameter() {
    }

    public Parameter(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return name + "=" + value;
    }
}