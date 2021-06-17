package org.jenkinsci.plugins.scriptler.util;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.jenkinsci.plugins.scriptler.config.Parameter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class UIHelper {

    /**
     * Extracts the parameters from the given request
     * 
     * @param req
     *            the request potentially containing parameters
     * @return parameters - might be an empty array, but never <code>null</code>.
     */
    public static Parameter[] extractParameters(JSONObject json) {
        Parameter[] parameters = new Parameter[0];
        final JSONObject defineParams = json.optJSONObject("defineParams");
        if (defineParams != null && !defineParams.isNullObject()) {
            JSONObject argsObj = defineParams.optJSONObject("parameters");
            if (argsObj == null) {
                JSONArray argsArrayObj = defineParams.optJSONArray("parameters");
                if (argsArrayObj != null) {
                    parameters = mapJsonArray(argsArrayObj, Parameter::new).toArray(new Parameter[0]);
                }
            } else {
                Parameter param = new Parameter(argsObj);
                parameters = new Parameter[] { param };
            }
        }
        return parameters;
    }

    private static <T> List<T> mapJsonArray(JSONArray array, Function<JSONObject, T> mapper) {
        List<T> list = new ArrayList<>(array.size());
        for (Object value : array) {
            assert JSONObject.class.isAssignableFrom(value.getClass());
            list.add(mapper.apply((JSONObject) value));
        }
        return list;
    }
}
