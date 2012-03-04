package org.jenkinsci.plugins.scriptler.util;

import javax.servlet.ServletException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.jenkinsci.plugins.scriptler.config.Parameter;

public class UIHelper {

    /**
     * Extracts the parameters from the given request
     * 
     * @param req
     *            the request potentially containing parameters
     * @return parameters - might be an empty array, but never <code>null</code>.
     * @throws ServletException
     */
    public static Parameter[] extractParameters(JSONObject json) throws ServletException {
        Parameter[] parameters = new Parameter[0];
        final JSONObject defineParams = json.getJSONObject("defineParams");
        if (!defineParams.isNullObject()) {
            JSONObject argsObj = defineParams.optJSONObject("parameters");
            if (argsObj == null) {
                JSONArray argsArrayObj = defineParams.optJSONArray("parameters");
                if (argsArrayObj != null) {
                    parameters = (Parameter[]) JSONArray.toArray(argsArrayObj, Parameter.class);
                }
            } else {
                Parameter param = (Parameter) JSONObject.toBean(argsObj, Parameter.class);
                parameters = new Parameter[] { param };
            }
        }
        return parameters;
    }
}
