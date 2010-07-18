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

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;


/**
 * @author domi
 * 
 */
public class ScriptSet {

	// have it sorted
	protected Set<Script> scriptSet = new TreeSet<Script>();

	public Script getScriptByName(String name) {
		for (Script scr : scriptSet) {
			if (scr.name.equals(name)) {
				return scr;
			}
		}
		return null;
	}

	public void removeScript(String name) {
		Script s = getScriptByName(name);
		scriptSet.remove(s);
	}

	public void addOrReplace(Script script) {
		if (script != null) {
			if (scriptSet.contains(script)) {
				scriptSet.remove(script);
			}
			scriptSet.add(script);
		}
	}

	public final Set<Script> getScripts() {
		return Collections.unmodifiableSet(scriptSet);
	}

	public void setScripts(Set<Script> scripts) {
		this.scriptSet = scripts;
	}

}
