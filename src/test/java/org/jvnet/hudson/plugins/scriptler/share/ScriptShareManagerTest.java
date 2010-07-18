package org.jvnet.hudson.plugins.scriptler.share;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

public class ScriptShareManagerTest {

	/**
	 * Tests if the default catalog file can be downloaded.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetScriptCatalog() throws Exception {
		File catalog = File.createTempFile("scriptler", ".xml");
		if (catalog.exists()) {
			catalog.delete();
		}
		Assert.assertTrue(catalog + " must be deleted befor test", !catalog.exists());
		ShareManager shareManager = new ShareManager();
		shareManager.downloadDefaultScriptCatalog(catalog);

		Assert.assertTrue(catalog + " not downloaded", catalog.exists());

		Catalog loadedCatalog = Catalog.load(catalog);

		Assert.assertNotNull("catalog not loaded", loadedCatalog);
	}

	@Test
	public void testSaveCatalog() throws Exception {
		Catalog cat = new Catalog("local");
		cat.addOrReplace(new CatalogEntry("name.groovy", "comment", "N/A", null));
		cat.save(new File("/localCatalog.xml"));
	}

}
