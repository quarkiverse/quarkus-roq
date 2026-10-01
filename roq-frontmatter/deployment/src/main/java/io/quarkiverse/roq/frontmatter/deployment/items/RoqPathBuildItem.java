package io.quarkiverse.roq.frontmatter.deployment.items;

import static io.quarkiverse.tools.stringpaths.StringPaths.removeLeadingSlash;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A url path claimed by a Roq source: a page, a static file, an alias or any other feature serving a path.
 * <p>
 * All claims are checked together in {@code RoqFrontMatterStep6BindProcessor#bindSelectedPaths}: a path claimed twice
 * fails the build (or logs a warning in dev mode), and each other path is selected in Roq Generator.
 */
public final class RoqPathBuildItem extends MultiBuildItem {

    private final String path;
    private final String source;

    /**
     * @param path the url path, with or without a leading slash (it is removed); the producer decides the trailing
     *        slash, e.g. {@code posts/hello/} for a page and {@code images/logo.png} for a file
     * @param source a description of what claims the path, shown in the conflict message, e.g.
     *        {@code page 'posts/hello.md'}
     */
    public RoqPathBuildItem(String path, String source) {
        this.path = removeLeadingSlash(path);
        this.source = source;
    }

    /**
     * @return the url path without leading slash
     */
    public String path() {
        return path;
    }

    /**
     * @return the description of what claims the path
     */
    public String source() {
        return source;
    }
}
