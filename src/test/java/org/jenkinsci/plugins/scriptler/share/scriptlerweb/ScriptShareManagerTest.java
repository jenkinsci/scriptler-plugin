package org.jenkinsci.plugins.scriptler.share.scriptlerweb;

import java.io.File;

import org.jenkinsci.plugins.scriptler.share.CatalogInfo;
import org.jenkinsci.plugins.scriptler.share.scriptlerweb.ScritplerWebCatalog.CatalogContent;
import org.junit.Assert;
import org.junit.Test;

public class ScriptShareManagerTest {

    private static String DEFAULT_LOCATION = "http://hudson.fortysix.ch/scriptler";

    public static String DEFAULT_CATALOG = DEFAULT_LOCATION + "/scriptler-catalog.xml";

    /**
     * Tests if the default catalog file can be downloaded. Event though we don't use the catalog from fortysix anymore, the format is the same as from scriptler web, therefore we can use it for the
     * test.
     */
    @Test
    public void testGetScriptCatalog() throws Exception {
        File catalog = File.createTempFile("scriptler", ".xml");
        catalog.deleteOnExit();
        CatalogManager shareManager = new CatalogManager(new CatalogInfo("name", DEFAULT_CATALOG, null, "scriptDownloadUrl"));
        shareManager.downloadDefaultScriptCatalog(catalog);

        Assert.assertTrue(catalog + " not downloaded", catalog.exists());

        CatalogContent loadedCatalog = CatalogContent.load(catalog);

        Assert.assertNotNull("catalog not loaded", loadedCatalog);
        Assert.assertNotNull("catalog entries is null", loadedCatalog.entrySet);
        Assert.assertTrue("no catalog entries", loadedCatalog.entrySet.size() > 0);

    }

}
