package org.jenkinsci.plugins.scriptler.share.scriptlerweb;

import java.util.List;

import org.jenkinsci.plugins.scriptler.share.ScriptInfo;

public class CatalogEntry extends ScriptInfo {

    private String id;
    @Deprecated
    private String provider;

    // keep to avoid unmarshaling errors
    @Deprecated
    private String url;

    @Override
    public List<Author> getAuthors() {
        final List<Author> as = super.getAuthors();
        if (provider != null && provider.length() > 0) {
            as.add(new Author(provider));
        }
        return as;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id
     *            the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

}
