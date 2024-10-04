package io.quarkiverse.roq.frontmatter.deployment.scan;

import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * A build item representing a Roq fm file.
 */
public final class RoqFrontMatterRawTemplateBuildItem extends MultiBuildItem {

    private final PageInfo info;

    /**
     * The layout used for this template
     */
    private final String layout;

    /**
     * true if it's a page template.
     * or false if it's an include template.
     *
     * Include template should not be published.
     */
    private final boolean isPage;

    /**
     * The FrontMatter data (it is not merged with parents at this stage)
     */
    private final JsonObject data;

    private final String collection;

    /**
     * The generated template content to be passed to be passed to Qute
     */
    private final String generatedTemplate;

    /**
     * Should this template be published
     */
    private final boolean published;

    public RoqFrontMatterRawTemplateBuildItem(PageInfo info, String layout, boolean isPage, JsonObject data, String collection,
            String generatedTemplate,
            boolean published) {
        this.info = info;
        this.layout = layout;
        this.isPage = isPage;
        this.data = data;
        this.collection = collection;
        this.generatedTemplate = generatedTemplate;
        this.published = published;
    }

    public boolean isPage() {
        return isPage;
    }

    public String id() {
        return info.id();
    }

    public PageInfo info() {
        return info;
    }

    public String layout() {
        return layout;
    }

    public JsonObject data() {
        return data;
    }

    public String collection() {
        return collection;
    }

    public String generatedContent() {
        return generatedTemplate;
    }

    public boolean published() {
        return published;
    }
}
