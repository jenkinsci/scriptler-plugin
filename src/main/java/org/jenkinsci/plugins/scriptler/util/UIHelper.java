package org.jenkinsci.plugins.scriptler.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.scriptler.config.Parameter;

public final class UIHelper {

    private UIHelper() {}

    /**
     * Extracts the parameters from the given request
     *
     * @param json
     *            the request potentially containing parameters
     * @return parameters - might be an empty array, but never <code>null</code>.
     */
    @NonNull
    public static List<Parameter> extractParameters(JSONObject json) {
        final JSONObject defineParams = json.optJSONObject("defineParams");
        if (defineParams == null || defineParams.isNullObject()) {
            // no parameters defined
            return List.of();
        }

        final List<Object> argsArray = Optional.<List<Object>>ofNullable(defineParams.optJSONArray("parameters"))
                .orElseGet(() -> {
                    JSONObject argsObj = defineParams.optJSONObject("parameters");
                    if (argsObj == null) {
                        return List.of();
                    }
                    return List.of(argsObj);
                });
        return mapJsonArray(argsArray, Parameter::new);
    }

    private static <T> List<T> mapJsonArray(List<Object> array, Function<JSONObject, T> mapper) {
        return array.stream()
                .filter(JSONObject.class::isInstance)
                .map(JSONObject.class::cast)
                .map(mapper)
                .toList();
    }
}
