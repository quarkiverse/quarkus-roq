package io.quarkiverse.roq.frontmatter.runtime.model;

import static io.quarkiverse.roq.util.PathUtils.addTrailingSlashIfNoExt;
import static io.quarkiverse.roq.util.PathUtils.prefixWithSlash;

import jakarta.enterprise.inject.Vetoed;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.qute.TemplateData;

/**
 * This represents the site root and allow to resolve other urls.
 */
@TemplateData
@Vetoed
public record RootUrl(
        /**
         * The site url (e.g. "https://example.com")
         */
        String url,

        /**
         * The application root path (e.g. "/my-root")
         */
        String rootPath) {
    public RootUrl(String url, String rootPath) {
        this.url = url;
        this.rootPath = prefixWithSlash(addTrailingSlashIfNoExt(rootPath));
    }

    /**
     * @return the absolute root url (site url + root path)
     */
    public String absolute() {
        return PathUtils.join(url, rootPath);
    }

    /**
     * @return the url path (e.g. "/my-root" or "/")
     */
    public String path() {
        return rootPath();
    }

    /**
     * @return same as {@link RootUrl#path()}
     */
    public String relative() {
        return rootPath();
    }

    /**
     * Resolve a path from the root url
     *
     * @param path the path to resolve
     * @return the new resolved RoqUrl
     */
    public RoqUrl resolve(String path) {
        return RoqUrl.fromRoot(this, path);
    }

    /**
     * {@see RootUrl.resolve(String path)}
     */
    public RoqUrl join(String path) {
        return resolve(path);
    }

    /**
     * Using a RootUrl as a String will print relative path
     */
    @Override
    public String toString() {
        return rootPath();
    }
}
