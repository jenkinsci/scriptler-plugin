/*
 * The MIT License
 *
 * Copyright (c) 2010, Dominik Bartholdi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.plugins.scriptler.share;

import hudson.ProxyConfiguration;
import hudson.Util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jvnet.hudson.plugins.scriptler.ScriptlerManagment;

/**
 * @author domi
 * 
 */
public class ShareManager {

	private static String DEFAULT = "http://hudson.fortysix.ch/scriptler";

	private static String DEFAULT_CATALOG = DEFAULT + "/scriptler-catalog.xml";

	public void downloadDefaultScriptCatalog(File catalogFileTarget) {

		try {
			FileOutputStream out = new FileOutputStream(catalogFileTarget);
			Util.copyStreamAndClose(ProxyConfiguration.open(new URL(DEFAULT_CATALOG)).getInputStream(), out);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public String downloadScript(String scriptName) {
		String source = null;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Util.copyStreamAndClose(ProxyConfiguration.open(new URL(DEFAULT + "/" + scriptName)).getInputStream(), out);
			source = out.toString("UTF-8");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return source;
	}

	/**
	 * Returns the newest catalog.
	 * 
	 * @return the catalog - never <code>null</code>, even if download failed.
	 */
	public Catalog loadCatalog() {
		File catFile = new File(ScriptlerManagment.getScriptlerHomeDirectory(), "catalog.xml");
		downloadDefaultScriptCatalog(catFile);
		Catalog catalog = null;
		if (catFile.exists()) {
			try {
				catalog = Catalog.load(catFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (catalog == null) {
			catalog = new Catalog(DEFAULT + " (Not reachable)");
		}
		return catalog;
	}
}
