package io.quarkiverse.roq.frontmatter.runtime.model;

import static io.quarkiverse.roq.util.PathUtils.addTrailingSlashIfNoExt;

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
 * @param path the path of the resource (e.g. "site/posts/hello-world/")
 */
@TemplateData
@Vetoed
public record RoqUrl(
        RootUrl root,
        String path) {

    public RoqUrl(RootUrl root, String path) {
        this.path = path;
        this.root = root;
    }

    /**
     * Using a RootUrl as a String will print relative url
     */
    @Override
    public String toString() {
        return relative();
    }

    /**
     * The relative url to this resource (including the root path).
     * If it is external it will return the external full url.
     * (e.g. /my-root/site/posts/hello-world/)
     */
    public String relative() {
        if (isExternal()) {
            return path();
        }
        return PathUtils.join(root.rootPath(), path());
    }

    /**
     * @return the absolute url to this resource (e.g. http://example.com/my-root/site/posts/hello-world/)
     */
    public String absolute() {
        if (isExternal()) {
            return path();
        }
        return PathUtils.join(root().absolute(), path());
    }

    /**
     * Encode this url to be used as a query parameter
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
        return new RoqUrl(root(), PathUtils.join(path(), addTrailingSlashIfNoExt(other.toString())));
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
        return new RoqUrl(root(), path() + other.toString());
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

    public static RoqUrl fromRoot(RootUrl root, String path) {
        if (isFullPath(path)) {
            return new RoqUrl(null, path);
        }
        return new RoqUrl(root, addTrailingSlashIfNoExt(path));
    }

}
