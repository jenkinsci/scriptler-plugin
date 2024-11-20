package org.jenkinsci.plugins.scriptler.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.jenkinsci.plugins.scriptler.config.Parameter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UIHelperTest {
    static Stream<Arguments> filesAndExpectedParameters() {
        return Stream.of(arguments("simple1", 2), arguments("simple2", 2), arguments("JENKINS-13518", 0));
    }

    @ParameterizedTest
    @MethodSource("filesAndExpectedParameters")
    void testExtractParameters(String fileName, int expectedParameters) throws Exception {
        JSONObject json = getJsonFromFile("/" + fileName + ".json");
        final Collection<Parameter> extractParameters = UIHelper.extractParameters(json);
        assertNotNull(extractParameters, "no parameters extracted");
        assertEquals(expectedParameters, extractParameters.size(), "not all params extracted");
    }

    private JSONObject getJsonFromFile(String resource) throws IOException, URISyntaxException {
        URL url = UIHelperTest.class.getResource(resource);
        final Path path = Paths.get(Objects.requireNonNull(url).toURI());
        String jsonTxt = Files.readString(path, StandardCharsets.UTF_8);
        return (JSONObject) JSONSerializer.toJSON(jsonTxt);
    }
}
