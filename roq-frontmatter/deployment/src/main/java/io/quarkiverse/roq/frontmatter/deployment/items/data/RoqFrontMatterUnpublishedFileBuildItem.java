package io.quarkiverse.roq.frontmatter.deployment.items.data;

import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Keeps a scanned static file (a page attachment or a public file) out of the published site: it is neither
 * served nor selected for static generation. Pages still see and resolve it (for instance as an image), so an
 * extension that turns the file into something else at build time can hide the original.
 * <p>
 * The resource path is the one of the matching {@link RoqFrontMatterStaticFileBuildItem#link()}: relative to the
 * site root, without a leading slash (for instance {@code images/cover.png} or {@code posts/hello/cover.png}).
 */
public final class RoqFrontMatterUnpublishedFileBuildItem extends MultiBuildItem {

    private final String resourcePath;

    public RoqFrontMatterUnpublishedFileBuildItem(String resourcePath) {
        this.resourcePath = Objects.requireNonNull(resourcePath, "resourcePath");
    }

    public String resourcePath() {
        return resourcePath;
    }

    @Override
    public String toString() {
        return "RoqFrontMatterUnpublishedFileBuildItem{" + resourcePath + "}";
    }
}
