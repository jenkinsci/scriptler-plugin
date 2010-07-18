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

import hudson.XmlFile;
import hudson.util.XStream2;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.thoughtworks.xstream.XStream;

/**
 * Represents a catalog with available scripts to download.
 */
public final class Catalog {

	// do not save to XML
	private transient CatalogInfo info;

	// have it sorted
	protected Set<CatalogEntry> entrySet = new HashSet<CatalogEntry>();

	public Catalog(CatalogInfo info) {
		this.info = info;
	}

	public void setInfo(CatalogInfo info) {
		this.info = info;
	}

	public CatalogInfo getInfo() {
		return info;
	}

	public CatalogEntry getEntryByName(String name) {
		for (CatalogEntry scr : entrySet) {
			if (scr.getName().equals(name)) {
				return scr;
			}
		}
		return null;
	}

	public void removeCatalogEntry(String name) {
		CatalogEntry s = getEntryByName(name);
		entrySet.remove(s);
	}

	public void addOrReplace(CatalogEntry script) {
		if (script != null) {
			if (entrySet.contains(script)) {
				entrySet.remove(script);
			}
			entrySet.add(script);
		}
	}

	public final Set<CatalogEntry> getEntries() {
		return Collections.unmodifiableSet(entrySet);
	}

	public void setEntries(Set<CatalogEntry> scripts) {
		this.entrySet = scripts;
	}

	public synchronized void save(File catalogFile) throws IOException {
		XmlFile f = getXmlFile(catalogFile);
		f.write(this);
	}

	private static XmlFile getXmlFile(File file) {
		return new XmlFile(XSTREAM, file);
	}

	public static Catalog load(File catalogFile) throws IOException {
		XmlFile f = getXmlFile(catalogFile);
		if (f.exists()) {
			// As it might be that we have an unsorted set, we ensure the
			// sorting at load time.
			Catalog sc = (Catalog) f.read();
			// SortedSet<CatalogEntry> sorted = new TreeSet<CatalogEntry>(new
			// ByNameSorter());
			// sorted.addAll(sc.getEntries());
			// sc.setEntries(sorted);
			return sc;
		} else {
			return null;
		}
	}

	private static final XStream XSTREAM = new XStream2();

	static {
		XSTREAM.alias("catalog", Catalog.class);
		XSTREAM.alias("entry", CatalogEntry.class);
	}

}
