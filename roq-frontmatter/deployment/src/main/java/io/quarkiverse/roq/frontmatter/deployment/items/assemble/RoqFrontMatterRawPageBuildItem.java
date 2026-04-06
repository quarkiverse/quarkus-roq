package io.quarkiverse.roq.frontmatter.deployment.items.assemble;

import java.util.List;

import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.qute.ParserConfig;
import io.vertx.core.json.JsonObject;

/**
 * A build item representing a processed content page (normal or document).
 * Data is not yet merged with parent layouts.
 * <p>
 * This can be produced by plugins to inject synthetic pages into the pipeline
 * (e.g. the faker plugin uses this to generate fake content).
 */
public final class RoqFrontMatterRawPageBuildItem extends MultiBuildItem {

    private final TemplateSource templateSource;
    private final String layout;
    private final JsonObject data;
    private final ConfiguredCollection collection;
    private final ParserConfig parserConfig;
    private final String generatedTemplate;
    private final String generatedContentTemplate;
    private final List<RoqFrontMatterAttachment> attachments;

    public RoqFrontMatterRawPageBuildItem(TemplateSource templateSource, String layout, JsonObject data,
            ConfiguredCollection collection, ParserConfig parserConfig, String generatedTemplate,
            String generatedContentTemplate,
            List<RoqFrontMatterAttachment> attachments) {
        this.templateSource = templateSource;
        this.layout = layout;
        this.data = data;
        this.collection = collection;
        this.parserConfig = parserConfig;
        this.generatedTemplate = generatedTemplate;
        this.generatedContentTemplate = generatedContentTemplate;
        this.attachments = attachments;
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

    public String collectionId() {
        return collection != null ? collection.id() : null;
    }

    public ParserConfig parserConfig() {
        return parserConfig;
    }

    public String generatedTemplate() {
        return generatedTemplate;
    }

    public String generatedContentTemplate() {
        return generatedContentTemplate;
    }

    public List<RoqFrontMatterAttachment> attachments() {
        return attachments;
    }
}
