package org.jvnet.hudson.plugins.scriptler.share;

import org.jvnet.hudson.plugins.scriptler.config.NamedResource;

public class CatalogEntry implements NamedResource {

	public final String name;
	public final String comment;
	public final String provider;
	public final String url;

	public CatalogEntry(String name, String comment, String provider, String url) {
		this.name = name;
		this.comment = comment;
		this.provider = provider;
		this.url = url;
	}

	public String getName() {
		return name;
	}
}
