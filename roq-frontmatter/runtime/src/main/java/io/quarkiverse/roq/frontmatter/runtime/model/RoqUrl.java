package io.quarkiverse.roq.frontmatter.runtime.model;

import static io.quarkiverse.roq.util.PathUtils.removeTrailingSlash;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.enterprise.inject.Vetoed;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.qute.TemplateData;

@TemplateData
@Vetoed
public record RoqUrl(RootUrl rootUrl, String path) {

    public RoqUrl(RootUrl rootUrl, String path) {
        this.path = path;
        this.rootUrl = rootUrl;
    }

    @Override
    public String toString() {
        return relative();
    }

    public String relative() {
        if (isExternal()) {
            return path();
        }
        return PathUtils.join(rootUrl.rootPath(), path());
    }

    public String absolute() {
        if (isExternal()) {
            return path();
        }
        return PathUtils.join(rootUrl().absolute(), path());
    }

    public String encoded() {
        return URLEncoder.encode(absolute(), StandardCharsets.UTF_8);
    }

    /**
     * Check if this is an absolute Url starting with http:// or https://
     *
     * @return true is it's an absolute url
     */
    public static boolean isPathAbsolute(String path) {
        return path.startsWith("http://") || path.startsWith("https://");
    }

    /**
     * Create a new Url joining the other path
     *
     * @param other the other path to join
     * @return the new joined url
     */
    public RoqUrl resolve(Object other) {
        if (isPathAbsolute(other.toString())) {
            return new RoqUrl(null, other.toString());
        }
        return new RoqUrl(rootUrl(), PathUtils.join(path(), removeTrailingSlash(other.toString())));
    }

    /**
     * {@see Resolvable#resolve}
     *
     * @param other the other path to join
     * @return the new joined url
     */
    public RoqUrl join(Object other) {
        return this.resolve(other);
    }

    /**
     * This will just append the given string (without adding the `/`)
     *
     * @param other the string to append
     * @return the concatenated url
     */
    public RoqUrl append(Object other) {
        return new RoqUrl(rootUrl(), path() + other.toString());
    }

    public boolean isExternal() {
        return rootUrl() == null;
    }

    public static RoqUrl fromRoot(RootUrl rootUrl, String path) {
        if (isPathAbsolute(path)) {
            return new RoqUrl(null, path);
        }
        return new RoqUrl(rootUrl, removeTrailingSlash(path));
    }

}
