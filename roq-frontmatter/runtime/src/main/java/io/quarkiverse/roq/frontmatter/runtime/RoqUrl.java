package io.quarkiverse.roq.frontmatter.runtime;

import io.quarkiverse.roq.util.PathUtils;

public record RoqUrl(String path) {

    /**
     * Create a new Url joining the other path
     *
     * @param other the other path to join
     * @return the new joined url
     */
    public RoqUrl resolve(Object other) {
        return new RoqUrl(PathUtils.join(path, other.toString()));
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
     * This will just append the given string
     *
     * @param other the string to append
     * @return the concatenated url
     */
    public RoqUrl append(Object other) {
        return new RoqUrl(path + other);
    }

    /**
     * Create a new Url from the given path/url
     *
     * @param from the url to join from
     * @return the new joined url
     */
    public RoqUrl from(Object from) {
        return new RoqUrl(PathUtils.join(from.toString(), path));
    }

    /**
     * Check if this is an absolute Url starting with http:// or https://
     *
     * @return true is it's an absolute url
     */
    public boolean isAbsolute() {
        return path.startsWith("http://") || path.startsWith("https://");
    }

    /**
     * Return itself if it absolute or from the given url if it's not.
     * This is useful for blog images which can be absolute or relative to the blog image directory.
     *
     * @param other the url to join from
     * @return either the url if it's absolute or the joined url if it's not
     */
    public RoqUrl absoluteOrElseFrom(Object other) {
        return isAbsolute() ? this : from(other);
    }

    @Override
    public String toString() {
        return path;
    }
}