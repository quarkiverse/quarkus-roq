package io.quarkiverse.roq.frontmatter.deployment.scan;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterLayoutTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * A build item representing a Roq FM file.
 * This template is just extracted from the disk, data is not yet merged with layouts.
 * <p>
 * Use {@link RoqFrontMatterLayoutTemplateBuildItem} to read all FM templates with
 * merged data.
 */
public final class RoqFrontMatterRawTemplateBuildItem extends MultiBuildItem {

    /**
     * The page details.
     */
    private final TemplateSource templateSource;

    /**
     * The layout used for this template.
     */
    private final String layout;

    /**
     * Include template should not be published.
     */
    private TemplateType type;

    /**
     * The FrontMatter data (it is not merged with parents at this stage).
     */
    private final JsonObject data;

    private ConfiguredCollection collection;

    /**
     * The generated Qute template with the layout include
     */
    private final String generatedTemplate;

    /**
     * The generated Qute template with just the content (without the layout include)
     */
    private final String generatedContentTemplate;

    /**
     * Static files attached to this template
     */
    private final List<Attachment> attachments;

    public RoqFrontMatterRawTemplateBuildItem(TemplateSource templateSource,
            String layout,
            TemplateType type,
            JsonObject data,
            ConfiguredCollection collection,
            String generatedTemplate,
            String generatedContentTemplate,
            List<Attachment> attachments) {
        this.templateSource = templateSource;
        this.layout = layout;
        this.type = type;
        this.data = data;
        this.collection = collection;
        this.generatedTemplate = generatedTemplate;
        this.generatedContentTemplate = generatedContentTemplate;
        this.attachments = attachments;
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

    public void type(TemplateType type) {
        Objects.requireNonNull(type);
        this.type = type;
    }

    public String id() {
        return templateSource.id();
    }

    public TemplateSource templateSource() {
        return templateSource;
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

    public void collection(ConfiguredCollection collection) {
        this.collection = collection;
    }

    public String collectionId() {
        return collection != null ? collection.id() : null;
    }

    public String generatedTemplate() {
        return generatedTemplate;
    }

    public String generatedContentTemplate() {
        return generatedContentTemplate;
    }

    public List<Attachment> attachments() {
        return attachments;
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

    public record Attachment(String name, Path path) {
    }
}
