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
package org.jenkinsci.plugins.scriptler.share.scriptlerweb;

import hudson.Util;
import hudson.ProxyConfiguration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jenkinsci.plugins.scriptler.ScriptlerManagment;
import org.jenkinsci.plugins.scriptler.share.CatalogInfo;
import org.jenkinsci.plugins.scriptler.share.scriptlerweb.ScritplerWebCatalog.CatalogContent;

/**
 * Manages the access to a catalogs information, and is able to download the scripts from it.
 * 
 * @see CatalogInfo
 * @author domi
 * 
 */
class CatalogManager {

    private final static Logger LOGGER = Logger.getLogger(CatalogManager.class.getName());

    private final CatalogInfo catalogInfo;

    public CatalogManager(CatalogInfo info) {
        this.catalogInfo = info;
    }

    /**
     * Downloads the catalog from the remote location. Package private for ease testing...
     * 
     * @param catalogFileTarget
     *            the file to be retrieved.
     */
    void downloadDefaultScriptCatalog(File catalogFileTarget) {

        try {
            FileOutputStream out = new FileOutputStream(catalogFileTarget);
            Util.copyStreamAndClose(ProxyConfiguration.open(new URL(catalogInfo.catalogLocation)).getInputStream(), out);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Get the requested script from the catalog location.
     * 
     * @param scriptName
     *            the name of the script. Will be used in the url.
     * @see CatalogInfo#catalogLocation
     * @return
     */
    public String downloadScript(String scriptName, String id) {
        String source = null;
        try {
            String fileUrl = catalogInfo.getReplacedDownloadUrl(scriptName, id);
            LOGGER.info("download script from: " + fileUrl);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Util.copyStreamAndClose(ProxyConfiguration.open(new URL(fileUrl)).getInputStream(), out);
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
    public CatalogContent loadCatalog() {
        File catFile = new File(ScriptlerManagment.getScriptlerHomeDirectory(), catalogInfo.name.trim() + "-catalog.xml");

        // right now we always download the file - this should be optimized
        // (maybe only once every hour?)
        downloadDefaultScriptCatalog(catFile);
        CatalogContent catalog = null;
        if (catFile.exists()) {
            try {
                catalog = CatalogContent.load(catFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (catalog == null) {
            catalog = new CatalogContent();
        }
        return catalog;
    }
}
