package io.quarkiverse.roq.frontmatter.runtime.model;

import static io.quarkiverse.roq.util.PathUtils.addTrailingSlashIfNoExt;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import jakarta.enterprise.inject.Vetoed;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.qute.TemplateData;

/**
 * This represents a Roq url for pages, resources, ...
 *
 * @param root the site url with root path included (e.g. "https://example.com/my-root/")
 * @param resourcePath the path of the resource (e.g. "site/posts/hello-world/") without the root path
 */
@TemplateData
@Vetoed
public record RoqUrl(
        RootUrl root,
        String resourcePath) {

    public RoqUrl(RootUrl root, String resourcePath) {
        this.resourcePath = resourcePath;
        this.root = root;
    }

    /**
     * Using a RootUrl as a String will print url path
     */
    @Override
    public String toString() {
        return path();
    }

    /**
     * Same as {@link #path(boolean)})} with encoded true
     */
    public String path() {
        return path(true);
    }

    /**
     * The url path to this resource including the root path (e.g. /my-root/site/posts/hello-world/).
     * If it is external it will return the external absolute url.
     *
     * @param encoded if true, the path special characters will be encoded using %, else it's kept untouched
     */
    public String path(boolean encoded) {
        if (isExternal()) {
            return resourcePath();
        }
        final String path = PathUtils.join(root.rootPath(), resourcePath());
        return encoded ? encode(path) : path;
    }

    private static String encode(String path) {
        try {
            return new URI(null, null, path, null).toASCIIString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return same as {@link #path()} ()}
     */
    public String relative() {
        return path();
    }

    /**
     * @return the absolute url to this resource (e.g. http://example.com/my-root/site/posts/hello-world/)
     */
    public String absolute() {
        if (isExternal()) {
            return resourcePath();
        }
        return PathUtils.join(root().url(), path());
    }

    /**
     * Encode this full url to be used as a query parameter
     */
    public String encoded() {
        return URLEncoder.encode(absolute(), StandardCharsets.UTF_8);
    }

    /**
     * Check if this is a full path starting with http:// or https://
     *
     * @return true is it's a full path url
     */
    public static boolean isFullPath(String path) {
        Objects.requireNonNull(path, "path is required");
        return path.startsWith("http://") || path.startsWith("https://");
    }

    /**
     * Create a new Url joining the other path.
     * Whatever if the path starts with `/`, it will always join.
     *
     * @param other the other path to join
     * @return the new joined url
     */
    public RoqUrl resolve(Object other) {
        if (isFullPath(other.toString())) {
            return new RoqUrl(null, other.toString());
        }
        return new RoqUrl(root(), PathUtils.join(resourcePath(), addTrailingSlashIfNoExt(other.toString())));
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
        return new RoqUrl(root(), resourcePath() + other.toString());
    }

    /**
     * @return true if this RoqUrl is external
     */
    public boolean isExternal() {
        return root() == null;
    }

    /**
     * resolve the path from the application root
     *
     * @param path the path to resolve
     * @return the resolved RoqUrl
     */
    public RoqUrl fromRoot(String path) {
        return this.root.resolve(path);
    }

    public static RoqUrl fromRoot(RootUrl root, String resourcePath) {
        if (isFullPath(resourcePath)) {
            return new RoqUrl(null, resourcePath);
        }
        return new RoqUrl(root, addTrailingSlashIfNoExt(resourcePath));
    }

}
