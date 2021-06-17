package org.jenkinsci.plugins.scriptler.config;

import net.sf.json.JSONObject;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Parameter implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String NAME = "name";
    private static final String VALUE = "value";
    private static final Set<String> PROPERTY_NAMES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(NAME, VALUE)));

    private final String name;
    private final String value;

    @SuppressWarnings("unchecked") // older untyped API
    public Parameter(JSONObject object) {
        Set<String> keys = object.keySet();
        if (!PROPERTY_NAMES.equals(keys)) {
            throw new IllegalArgumentException("Provided JSONObject does not appear to be a Parameter");
        }
        name = object.getString(NAME);
        value = object.getString(VALUE);
    }

    public Parameter(String name, String value) {
        this.name = name;
        this.value = value;
    }
    
    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return name + "=" + value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Parameter parameter = (Parameter) o;

        if (name != null ? !name.equals(parameter.name) : parameter.name != null)
            return false;

        return value != null ? value.equals(parameter.value) : parameter.value == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}
