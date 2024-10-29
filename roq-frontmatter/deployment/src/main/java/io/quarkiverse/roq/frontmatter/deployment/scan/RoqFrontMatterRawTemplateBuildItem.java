package io.quarkiverse.roq.frontmatter.deployment.scan;

import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * A build item representing a Roq FM file.
 * This template is just extracted from the disk, data is not yet merged with layouts.
 * <p>
 * Use {@link io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterTemplateBuildItem} to read all FM templates with
 * merged data.
 */
public final class RoqFrontMatterRawTemplateBuildItem extends MultiBuildItem {

    /**
     * The page details.
     */
    private final PageInfo info;

    /**
     * The layout used for this template.
     */
    private final String layout;

    /**
     * Include template should not be published.
     */
    private final TemplateType type;

    /**
     * The FrontMatter data (it is not merged with parents at this stage).
     */
    private final JsonObject data;

    private final ConfiguredCollection collection;

    /**
     * The generated template content to be passed to be passed to Qute.
     */
    private final String generatedTemplate;

    /**
     * Should this template be published (published templates can be available in the data, but hidden from routing).
     */
    private final boolean published;

    public RoqFrontMatterRawTemplateBuildItem(PageInfo info, String layout, TemplateType type, JsonObject data,
            ConfiguredCollection collection,
            String generatedTemplate,
            boolean published) {
        this.info = info;
        this.layout = layout;
        this.type = type;
        this.data = data;
        this.collection = collection;
        this.generatedTemplate = generatedTemplate;
        this.published = published;
    }

    public boolean isPage() {
        return type.isPage();
    }

    public boolean isLayout() {
        return type.isLayout();
    }

    public TemplateType type() {
        return type;
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

    public ConfiguredCollection collection() {
        return collection;
    }

    public String collectionId() {
        return collection != null ? collection.id() : null;
    }

    public String generatedTemplate() {
        return generatedTemplate;
    }

    public boolean published() {
        return published;
    }

    public enum TemplateType {
        DOCUMENT_PAGE,
        NORMAL_PAGE,
        THEME_LAYOUT,
        LAYOUT;

        public boolean isPage() {
            return this == DOCUMENT_PAGE || this == NORMAL_PAGE;
        }

        public boolean isLayout() {
            return this == LAYOUT;
        }

        public boolean isThemeLayout() {
            return this == THEME_LAYOUT;
        }

    }
}
