package io.quarkiverse.roq.plugin.ogcard.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.plugin.ogcard.runtime.OgCardConfig;
import io.quarkiverse.roq.plugin.ogcard.runtime.model.OgCardTarget;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

class OgCardTargetResolverTest {

    @Test
    void pngPathForPostsCollection() {
        OgCardConfig config = config("quarkus.roq.plugin.og-card.output-prefix=/og");

        assertThat(OgCardTargetResolver.pngPath(config, "/posts/start-here/", "posts", "start-here"))
                .isEqualTo("/og/posts/start-here.png");
    }

    @Test
    void pngPathForHomepage() {
        OgCardConfig config = config("quarkus.roq.plugin.og-card.output-prefix=/og");

        assertThat(OgCardTargetResolver.pngPath(config, "/", null, "index"))
                .isEqualTo("/og/index.png");
    }

    @Test
    void imageAltUsesSiteNameForCollectionPages() {
        assertThat(OgCardTargetResolver.imageAlt("ROQ", "Start here", "/posts/start-here/"))
                .isEqualTo("ROQ — Start here");
    }

    @Test
    void truncateAddsEllipsis() {
        String longText = "a".repeat(250);
        assertThat(OgCardTargetResolver.truncate(longText, 200)).hasSize(200).endsWith("…");
    }

    @Test
    void matchesAndInjectsForHomepage() {
        OgCardConfig config = config("quarkus.roq.plugin.og-card.include-paths=/");
        RoqFrontMatterDataModificationBuildItem.SourceData source = new RoqFrontMatterDataModificationBuildItem.SourceData(
                null, "index.html", null, true,
                new io.vertx.core.json.JsonObject().put("title", "Home").put("description", "Desc"));

        OgCardTargetResolver.PageContext pageContext = OgCardTargetResolver.pageContextFromSource(source);
        assertThat(OgCardTargetResolver.matchesSource(config, source, pageContext.pagePath(), pageContext.collectionId(),
                pageContext.siteIndex())).isTrue();

        OgCardTarget target = OgCardTargetResolver.targetFromSource(config, source, pageContext.pagePath(),
                pageContext.collectionId(), pageContext.slug(), pageContext.siteIndex());
        assertThat(target).isNotNull();
        assertThat(target.pngPath()).isEqualTo("/og/index.png");
    }

    @Test
    void excludePathsApplyToCollectionsNotIncludePaths() {
        OgCardConfig config = config(
                "quarkus.roq.plugin.og-card.collections=posts",
                "quarkus.roq.plugin.og-card.include-paths=/about/",
                "quarkus.roq.plugin.og-card.exclude-paths=/posts/tag/,/about/");

        assertThat(OgCardTargetResolver.matchesSource(config,
                source("posts/tag/cool.md", "posts", "title", "Tag page"),
                "/posts/tag/cool/", "posts", false)).isFalse();

        assertThat(OgCardTargetResolver.matchesSource(config,
                source("about.md", null, "title", "About"),
                "/about/", null, false)).isTrue();
    }

    private static RoqFrontMatterDataModificationBuildItem.SourceData source(
            String relativePath, String collectionId, String titleKey, String title) {
        io.vertx.core.json.JsonObject fm = new io.vertx.core.json.JsonObject().put(titleKey, title);
        ConfiguredCollection collection = collectionId == null ? null
                : new ConfiguredCollection(collectionId, false, false, false, "default", null, Optional.empty());
        return new RoqFrontMatterDataModificationBuildItem.SourceData(
                null, relativePath, collection, true, fm);
    }

    private static OgCardConfig config(String... keyValues) {
        SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder()
                .withMapping(OgCardConfig.class)
                .withValidateUnknown(false);
        for (String keyValue : keyValues) {
            int idx = keyValue.indexOf('=');
            builder.withDefaultValue(keyValue.substring(0, idx), keyValue.substring(idx + 1));
        }
        SmallRyeConfig config = builder.build();
        return config.getConfigMapping(OgCardConfig.class);
    }
}
