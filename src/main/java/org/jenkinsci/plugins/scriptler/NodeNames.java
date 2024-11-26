package org.jenkinsci.plugins.scriptler;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NodeNames {
    public static final String BUILT_IN = "(built-in)";
    public static final String ALL = "(all)";
    public static final String ALL_AGENTS = "(all agents)";
    private static final Map<String, String> DEPRECATED_ALIASES;

    static {
        Map<String, List<String>> deprecatedNames =
                Map.of(BUILT_IN, List.of("(master)", "(controller)"), ALL_AGENTS, List.of("(all slaves)"));

        Map<String, String> aliases = new HashMap<>();
        deprecatedNames.forEach(
                (newName, oldNames) -> oldNames.forEach(oldName -> aliases.put(normalizeName(oldName), newName)));

        DEPRECATED_ALIASES = Map.copyOf(aliases);
    }

    @NonNull
    private static String normalizeName(@NonNull String name) {
        return name.toLowerCase();
    }

    @NonNull
    public static String normalizeNodeName(@NonNull String nodeName) {
        return DEPRECATED_ALIASES.getOrDefault(normalizeName(nodeName), nodeName);
    }

    private NodeNames() {}
}
