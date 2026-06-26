package io.quarkiverse.roq.frontmatter.deployment.items.data;

import io.quarkus.builder.item.MultiBuildItem;

public final class RoqFrontMatterFileNameAliasBuildItem extends MultiBuildItem {
    private final String aliasResourcePath;
    private final String canonicalResourcePath;

    public RoqFrontMatterFileNameAliasBuildItem(String aliasResourcePath, String canonicalResourcePath) {
        this.aliasResourcePath = aliasResourcePath;
        this.canonicalResourcePath = canonicalResourcePath;
    }

    public String aliasResourcePath() {
        return aliasResourcePath;
    }

    public String canonicalResourcePath() {
        return canonicalResourcePath;
    }
}
