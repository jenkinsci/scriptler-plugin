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
package org.jvnet.hudson.plugins.scriptler.config;

import hudson.BulkChange;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.util.XStream2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jvnet.hudson.plugins.scriptler.ScriptlerManagment;
import org.jvnet.hudson.plugins.scriptler.share.CatalogInfo;
import org.jvnet.hudson.plugins.scriptler.util.ByNameSorter;

import com.thoughtworks.xstream.XStream;

/**
 */
public final class ScriptlerConfiguration extends ScriptSet implements Saveable {

	private List<CatalogInfo> catalogInfos = new ArrayList<CatalogInfo>();

	public ScriptlerConfiguration(SortedSet<Script> scripts) {
		if (scripts != null) {
			this.scriptSet = scripts;
		}
	}

	public List<CatalogInfo> getCatalogInfos() {
		return catalogInfos;
	}

	public void setCatalogInfos(List<CatalogInfo> catalogInfos) {
		this.catalogInfos = catalogInfos;
	}

	/**
	 * Gets the catalog info by its name.
	 * 
	 * @param name
	 *            the name of the catalog to search for
	 * @return <code>null</code> if no info with the given name can be found.
	 */
	public CatalogInfo getCatalogInfo(String name) {
		for (CatalogInfo catInfo : getCatalogInfos()) {
			if (catInfo.name.equals(name)) {
				return catInfo;
			}
		}
		return null;
	}

	public synchronized void save() throws IOException {
		if (BulkChange.contains(this))
			return;
		getXmlFile().write(this);
		SaveableListener.fireOnChange(this, getXmlFile());
	}

	public static XmlFile getXmlFile() {
		return new XmlFile(XSTREAM, new File(ScriptlerManagment.getScriptlerHomeDirectory(), "scriptler.xml"));
	}

	public static ScriptlerConfiguration load() throws IOException {
		XmlFile f = getXmlFile();
		if (f.exists()) {
			// As it might be that we have an unsorted set, we ensure the
			// sorting at load time.
			ScriptlerConfiguration sc = (ScriptlerConfiguration) f.read();
			SortedSet<Script> sorted = new TreeSet<Script>(new ByNameSorter());
			sorted.addAll(sc.getScripts());
			sc.setScripts(sorted);
			return sc;
		} else {
			return null;
		}
	}

	private static final XStream XSTREAM = new XStream2();

	static {
		XSTREAM.alias("scriptler", ScriptlerConfiguration.class);
		XSTREAM.alias("script", Script.class);
		XSTREAM.alias("catalog", CatalogInfo.class);
	}

}
