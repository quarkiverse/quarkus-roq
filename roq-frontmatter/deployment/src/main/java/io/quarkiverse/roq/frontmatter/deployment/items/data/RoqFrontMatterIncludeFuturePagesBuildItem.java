package io.quarkiverse.roq.frontmatter.deployment.items.data;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Marker build item that tells the data processor to skip the build-time future date filter.
 * When present, future-dated pages are included in the route map and date-checked at runtime instead.
 */
public final class RoqFrontMatterIncludeFuturePagesBuildItem extends SimpleBuildItem {
}
