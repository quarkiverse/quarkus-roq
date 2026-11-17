package io.quarkiverse.roq.frontmatter.deployment.items.data;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * The Markdown of one page, converted once from its source, with the site-relative path it is published at. Produced by
 * the Markdown twin plugin, one item per page that gets a twin, for every build step that needs a page as Markdown,
 * such as the static twin file it writes itself or an llms.txt that links the twins.
 */
public final class RoqPageMarkdownBuildItem extends MultiBuildItem {

    private final RoqFrontMatterPageTemplateBuildItem page;
    private final String path;
    private final String markdown;

    /**
     * @param page the page the Markdown comes from
     * @param path where the Markdown is published, relative to the site root, such as {@code docs/basics/index.md}
     * @param markdown the Markdown, ending with a line feed
     */
    public RoqPageMarkdownBuildItem(RoqFrontMatterPageTemplateBuildItem page, String path, String markdown) {
        this.page = page;
        this.path = path;
        this.markdown = markdown;
    }

    public RoqFrontMatterPageTemplateBuildItem page() {
        return page;
    }

    public String path() {
        return path;
    }

    public String markdown() {
        return markdown;
    }
}
