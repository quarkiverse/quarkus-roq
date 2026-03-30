package io.quarkiverse.roq.frontmatter.deployment.items.assemble;

import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * A build item representing a processed layout template.
 * Data is not yet merged with parent layouts.
 */
public final class RoqFrontMatterRawLayoutBuildItem extends MultiBuildItem {

    private final TemplateSource templateSource;
    private final String layout;
    private final JsonObject data;
    private final String generatedTemplate;
    private final boolean themeLayout;

    public RoqFrontMatterRawLayoutBuildItem(TemplateSource templateSource, String layout, JsonObject data,
            String generatedTemplate, boolean themeLayout) {
        this.templateSource = templateSource;
        this.layout = layout;
        this.data = data;
        this.generatedTemplate = generatedTemplate;
        this.themeLayout = themeLayout;
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

    public String generatedTemplate() {
        return generatedTemplate;
    }

    public boolean isThemeLayout() {
        return themeLayout;
    }
}
