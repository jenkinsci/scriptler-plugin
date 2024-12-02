package org.jenkinsci.plugins.scriptler.share.gh;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.ProxyConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.scriptler.share.CatalogInfo;
import org.jenkinsci.plugins.scriptler.share.ScriptInfo;
import org.jenkinsci.plugins.scriptler.share.ScriptInfoCatalog;

/**
 * Provides access to the scriptler scripts shared at <a href="https://github.com/jenkinsci/jenkins-scripts">jenkinsci/jenkins-scripts</a>
 *
 * @author Dominik Bartholdi (imod)
 *
 */
@Extension(ordinal = 10)
public class GHCatalog implements ScriptInfoCatalog<ScriptInfo> {

    private static final Logger LOGGER = Logger.getLogger(GHCatalog.class.getName());

    private static final String REPO = "jenkinsci/jenkins-scripts";
    private static final String BRANCH = "main";
    public static final String REPO_BASE = "https://github.com/" + REPO + "/blob/" + BRANCH + "/scriptler/{1}";
    public static final String DOWNLOAD_URL = "https://raw.github.com/" + REPO + "/" + BRANCH + "/scriptler/{1}";

    public static final CatalogInfo CATALOG_INFO = new CatalogInfo("gh", REPO_BASE, REPO_BASE, DOWNLOAD_URL);

    @Override
    public List<ScriptInfo> getEntries() {
        return getEntries(ScriptInfo.COMPARATOR_BY_NAME);
    }

    @Override
    public String getDisplayName() {
        return "GitHub";
    }

    @Override
    public ScriptInfo getEntryById(String id) {
        for (ScriptInfo info : getEntries(null)) {
            if (id.equals(info.getId())) {
                return info;
            }
        }
        return null;
    }

    private List<ScriptInfo> getEntries(@CheckForNull Comparator<ScriptInfo> comparator) {
        Collection<ScriptInfo> scriptInfoList = List.of();
        try {
            scriptInfoList = CentralScriptJsonCatalog.getCatalog().getScripts();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "not abe to load script infos from GH", e);
        }
        List<ScriptInfo> sortedScriptInfoList = new ArrayList<>(scriptInfoList);

        if (comparator != null) sortedScriptInfoList.sort(comparator);

        return sortedScriptInfoList;
    }

    @Override
    public CatalogInfo getInfo() {
        return CATALOG_INFO;
    }

    @Override
    public String getScriptSource(ScriptInfo scriptInfo) {

        final String scriptUrl = CATALOG_INFO.getReplacedDownloadUrl(scriptInfo.getName(), scriptInfo.getId());
        try {
            HttpClient client = ProxyConfiguration.newHttpClient();
            HttpRequest request =
                    ProxyConfiguration.newHttpRequestBuilder(new URI(scriptUrl)).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .body();
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, e, () -> "not able to load script sources from GH for: " + scriptInfo);
            Thread.currentThread().interrupt();
        } catch (IOException | URISyntaxException e) {
            LOGGER.log(Level.SEVERE, e, () -> "not able to load script sources from GH for: " + scriptInfo);
        }

        return null;
    }
}
