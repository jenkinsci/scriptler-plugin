package org.jenkinsci.plugins.scriptler.util;

import junit.framework.TestCase;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.scriptler.config.Parameter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

public class UIHelperTest  extends TestCase {
    public void testExtractParameters1() throws Exception {
        JSONObject json = getJsonFromFile("/simple1.json");
        final Collection<Parameter> extractParameters = UIHelper.extractParameters(json);
        assertNotNull("no parameters extracted", extractParameters);
        assertEquals("not all params extracted", 2, extractParameters.size());
    }
    
    public void testExtractParameters2() throws Exception {
        JSONObject json = getJsonFromFile("/simple2.json");
        final Collection<Parameter> extractParameters = UIHelper.extractParameters(json);
        assertNotNull("no parameters extracted", extractParameters);
        assertEquals("not all params extracted", 2, extractParameters.size());
    }
    
    public void testExtractParameters_JENKINS_13518() throws Exception {
        JSONObject json = getJsonFromFile("/JENKINS-13518.json");
        final Collection<Parameter> extractParameters = UIHelper.extractParameters(json);
        assertNotNull("no parameters extracted", extractParameters);
        assertEquals("not all params extracted", 0, extractParameters.size());
    }
    
    private JSONObject getJsonFromFile(String resource) throws IOException {
        InputStream is = UIHelperTest.class.getResourceAsStream(resource);
        String jsonTxt = IOUtils.toString(is);
        JSONObject json = (JSONObject) JSONSerializer.toJSON(jsonTxt);
        return json;
    }
}
