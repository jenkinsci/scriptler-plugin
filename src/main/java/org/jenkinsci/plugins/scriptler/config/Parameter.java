package org.jenkinsci.plugins.scriptler.config;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

public class Parameter implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final String KEY_NAME = "name";
    private static final String KEY_VALUE = "value";
    private static final Set<String> PROPERTY_NAMES = Set.of(KEY_NAME, KEY_VALUE);

    private final String name;
    private final String value;

    public Parameter(JSONObject object) {
        Set<String> keys = object.keySet();
        if (!PROPERTY_NAMES.equals(keys)) {
            throw new IllegalArgumentException("Provided JSONObject does not appear to be a Parameter");
        }
        name = object.getString(KEY_NAME);
        value = object.getString(KEY_VALUE);
    }

    @DataBoundConstructor
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Parameter parameter = (Parameter) o;
        return Objects.equals(name, parameter.name) && Objects.equals(value, parameter.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }
}
