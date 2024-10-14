package io.quarkiverse.roq.plugin.aliases.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

public final class RoqFrontMatterAliasesBuildItem extends MultiBuildItem {

    /**
     * Represents an alias of a determined link.
     */
    private final String alias;

    /**
     * The link where the {@code aliases} are pointing to.
     */
    private final String target;

    public RoqFrontMatterAliasesBuildItem(String alias, String target) {
        this.alias = alias;
        this.target = target;
    }

    public String alias() {
        return alias;
    }

    public String target() {
        return target;
    }
}
