package org.jenkinsci.plugins.scriptler.share;

import hudson.Util;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jenkinsci.plugins.scriptler.config.NamedResource;

public class ScriptInfo implements NamedResource {
    protected String script;
    protected String comment;
    protected String core;
    protected String name;
    protected List<Author> authors;
    protected List<String> parameters;

    public String getId() {
        return script;
    }

    public String getName() {
        return name;
    }

    public String getNonNullName() {
        return Util.fixNull(name);
    }

    public void addAuthor(Author author) {
        if (authors == null) {
            authors = new ArrayList<>();
        }
        authors.add(author);
    }

    public List<String> getParameters() {
        return parameters == null ? List.of() : List.copyOf(parameters);
    }

    public void setParameters(List<String> parameters) {
        this.parameters = new ArrayList<>(parameters);
    }

    public void setAuthors(List<Author> authors) {
        this.authors = new ArrayList<>(authors);
    }

    public List<Author> getAuthors() {
        return authors == null ? List.of() : List.copyOf(authors);
    }

    @Override
    public String toString() {
        return "[ScriptInfo: name=" + name + "]";
    }

    /**
     * @return the script
     */
    public String getScript() {
        return script;
    }

    /**
     * @param script
     *            the script to set
     */
    public void setScript(String script) {
        this.script = script;
    }

    /**
     * @return the comment
     */
    public String getComment() {
        return comment;
    }

    /**
     * @param comment
     *            the comment to set
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * @return the core
     */
    public String getCore() {
        return core;
    }

    /**
     * @param core
     *            the core to set
     */
    public void setCore(String core) {
        this.core = core;
    }

    /**
     * @param name
     *            the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    public static class Author {
        private String name;

        public Author() {}

        public Author(String name) {
            this.name = name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class Parameter {
        private String name;

        public Parameter() {}

        public Parameter(String name) {
            this.name = name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static final Comparator<ScriptInfo> COMPARATOR_BY_NAME =
            Comparator.comparing(ScriptInfo::getNonNullName, String.CASE_INSENSITIVE_ORDER);
}
