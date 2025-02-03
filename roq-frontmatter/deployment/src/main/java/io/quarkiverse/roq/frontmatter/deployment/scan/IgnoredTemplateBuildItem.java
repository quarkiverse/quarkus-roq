package io.quarkiverse.roq.frontmatter.deployment.scan;

import io.quarkus.builder.item.MultiBuildItem;

public final class IgnoredTemplateBuildItem extends MultiBuildItem {
    private final String templateId;

    public IgnoredTemplateBuildItem(String templateId) {
        this.templateId = templateId;
    }

    public String templateId() {
        return templateId;
    }
}
