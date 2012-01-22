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

import hudson.Extension;
import hudson.XmlFile;
import hudson.util.XStream2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jenkinsci.plugins.scriptler.share.CatalogInfo;
import org.jenkinsci.plugins.scriptler.share.ScriptInfoCatalog;

import com.thoughtworks.xstream.XStream;

/**
 * Represents a catalog with available scripts to download.
 */
@Extension(ordinal = 5)
public class ScritplerWebCatalog extends ScriptInfoCatalog<CatalogEntry> {

    public static final CatalogInfo CATALOG_INFO = new CatalogInfo("scriptlerweb", "http://scriptlerweb.appspot.com/catalog/xml",
            "http://scriptlerweb.appspot.com/script/show/{1}", "http://scriptlerweb.appspot.com/script/download/{1}");

    private static final CatalogManager CATALOG_MANAGER = new CatalogManager(CATALOG_INFO);

    @Override
    public CatalogInfo getInfo() {
        return CATALOG_INFO;
    }

    @Override
    public String getDisplayName() {
        return "ScriptlerWeb";
    }

    @Override
    public CatalogEntry getEntryById(String id) {
        for (CatalogEntry scr : getEntries()) {
            if (scr.id != null && scr.id.equals(id)) {
                return scr;
            }
        }
        return null;
    }

    @Override
    public List<CatalogEntry> getEntries() {
        return new ArrayList<CatalogEntry>(CATALOG_MANAGER.loadCatalog().entrySet);
    }

    @Override
    public String getScriptSource(CatalogEntry scriptInfo) {
        return CATALOG_MANAGER.downloadScript(scriptInfo.name, scriptInfo.id);
    }

    public static class CatalogContent {
        private static final XStream XSTREAM = new XStream2();

        protected Set<CatalogEntry> entrySet = new HashSet<CatalogEntry>();

        public static CatalogContent load(File catalogFile) throws IOException {
            XmlFile f = getXmlFile(catalogFile);
            if (f.exists()) {
                CatalogContent sc = (CatalogContent) f.read();
                return sc;
            } else {
                return null;
            }
        }

        public synchronized void save(File catalogFile) throws IOException {
            XmlFile f = getXmlFile(catalogFile);
            f.write(this);
        }

        private static XmlFile getXmlFile(File file) {
            return new XmlFile(XSTREAM, file);
        }

        static {
            XSTREAM.alias("catalog", CatalogContent.class);
            XSTREAM.alias("entry", CatalogEntry.class);
        }
    }
}
