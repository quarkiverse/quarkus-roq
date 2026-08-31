package io.quarkiverse.roq.frontmatter.runtime.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

import jakarta.enterprise.inject.Vetoed;

import io.quarkiverse.tools.stringpaths.StringPaths;
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

    /**
     * The accepted URI schemes (lowercase) considered as full/external paths. Any link with one of
     * these schemes is preserved as-is instead of being resolved as a relative path against the site root.
     * <p>
     * To add support for an additional scheme, add it here.
     */
    private static final Set<String> ACCEPTED_URI_SCHEMES = Set.of(
            "http", "https", "mailto", "tel", "data", "javascript", "ftp", "ftps",
            "irc", "file", "git", "ssh", "sftp", "sms", "geo", "news", "nntp",
            "magnet", "bitcoin", "ethereum", "skype", "facetime", "whatsapp");

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
        final String path = StringPaths.join(root.rootPath(), resourcePath());
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
        return StringPaths.join(root().url(), path());
    }

    /**
     * Encode this full url to be used as a query parameter
     */
    public String encoded() {
        return URLEncoder.encode(absolute(), StandardCharsets.UTF_8);
    }

    /**
     * Check if this is a full path starting with one of the {@link #ACCEPTED_URI_SCHEMES}.
     *
     * @return true if it's a full path url
     */
    public static boolean isFullPath(String path) {
        Objects.requireNonNull(path, "path is required");
        int colonIndex = path.indexOf(':');
        if (colonIndex <= 0) {
            return false;
        }
        return ACCEPTED_URI_SCHEMES.contains(path.substring(0, colonIndex).toLowerCase());
    }

    /**
     * Checks if this is an absolute path (starting with /)
     *
     * @return true if the path starts with a slash
     */
    public static boolean isAbsolute(String path) {
        return path.startsWith("/");
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
        return new RoqUrl(root(), StringPaths.join(resourcePath(), other.toString()));
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
     * Resolve a page attachment against this url. When the url targets an html file
     * (e.g. `/posts/my-post.html` produced by a `:ext!` link), the extension is removed
     * so attachments resolve under the page directory (e.g. `/posts/my-post/img.png`).
     *
     * @param name the attachment file name or relative path
     * @return the attachment url
     */
    public RoqUrl resolveAttachment(Object name) {
        if (!isExternal() && resourcePath().endsWith(".html")) {
            return new RoqUrl(root(), StringPaths.removeExtension(resourcePath())).resolve(name);
        }
        return resolve(name);
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
        return new RoqUrl(root, resourcePath);
    }

    /**
     * Check if the URL path starts with the given prefix
     *
     * @param prefix the prefix to check
     * @return true if the path starts with the prefix
     */
    public boolean startsWith(String prefix) {
        return path().startsWith(prefix);
    }

    /**
     * Check if the URL path contains the given string.
     *
     * @param str the string to search for
     * @return true if the path contains the string
     */
    public boolean contains(String str) {
        return path().contains(str);
    }

    /**
     * Replace all literal occurrences of target with replacement.
     *
     * @param target the string to replace
     * @param replacement the replacement string
     * @return a new RoqUrl with the replaced path
     */
    public RoqUrl replace(String target, String replacement) {
        String newPath = resourcePath().replace(target, replacement);
        return new RoqUrl(root(), newPath);
    }

    /**
     * Replace all occurrences matching the regex with the replacement.
     *
     * @param regex the regular expression
     * @param replacement the replacement string
     * @return a new RoqUrl with the replaced path
     */
    public RoqUrl replaceAll(String regex, String replacement) {
        String newPath = resourcePath().replaceAll(regex, replacement);
        return new RoqUrl(root(), newPath);
    }

    /**
     * Check if this url path matches one of the given paths exactly (ignoring trailing slash).
     *
     * @param path the path to check against (including root path if configured)
     * @param paths additional paths to check against
     */
    public boolean isActive(String path, String... paths) {
        final String current = StringPaths.removeTrailingSlash(path());
        if (path != null && current.equals(StringPaths.removeTrailingSlash(path))) {
            return true;
        }
        for (String p : paths) {
            if (p != null && current.equals(StringPaths.removeTrailingSlash(p))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove the first occurrence of the given string from the path
     *
     * @param str the string to remove
     * @return a new RoqUrl with the string removed
     */
    public RoqUrl removeFirst(String str) {
        String newPath = resourcePath().replaceFirst(java.util.regex.Pattern.quote(str), "");
        return new RoqUrl(root(), newPath);
    }

    /**
     * Extracts the collection base path from a page URL to use as relfileprefix.
     * Examples:
     * - /guides/my-doc → /guides/
     * - /version/main/guides/security → /version/main/guides/
     * - /blog/my-post/ → /blog/
     *
     * @param pageUrl the absolute page URL
     * @return the collection base path with trailing slash, or null if cannot be determined
     */
    public static String parentPath(String pageUrl) {
        if (pageUrl == null || pageUrl.isEmpty() || pageUrl.equals("/")) {
            return null;
        }

        String path = isAbsolute(pageUrl) ? pageUrl.substring(1) : pageUrl;

        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) {
            // No slash means single-level like /my-doc, return / as base
            return "/";
        }

        // Return everything up to and including the last slash before the page name
        return "/" + path.substring(0, lastSlash + 1);
    }

}
