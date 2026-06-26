package io.quarkiverse.roq.plugin.ogimage.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.plugin.ogimage.runtime.OgImageConfig;
import io.quarkiverse.roq.plugin.ogimage.runtime.model.OgImageTarget;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

class OgImageTargetResolverTest {

    @Test
    void pngPathForPosterCollection() {
        OgImageConfig config = config("quarkus.roq.plugin.og-image.output-prefix=/og");

        assertThat(OgImageTargetResolver.pngPath(config, "/poster/start-here/", "poster", "start-here"))
                .isEqualTo("/og/posters/start-here.png");
    }

    @Test
    void pngPathForHomepage() {
        OgImageConfig config = config("quarkus.roq.plugin.og-image.output-prefix=/og");

        assertThat(OgImageTargetResolver.pngPath(config, "/", null, "index"))
                .isEqualTo("/og/index.png");
    }

    @Test
    void imageAltUsesSiteNameForPosterPages() {
        assertThat(OgImageTargetResolver.imageAlt("Bob Playbook", "Start here with Bob", "/poster/start-here/"))
                .isEqualTo("Bob Playbook — Start here with Bob");
    }

    @Test
    void truncateAddsEllipsis() {
        String longText = "a".repeat(250);
        assertThat(OgImageTargetResolver.truncate(longText, 200)).hasSize(200).endsWith("…");
    }

    @Test
    void matchesAndInjectsForHomepage() {
        OgImageConfig config = config("quarkus.roq.plugin.og-image.include-paths=/");
        RoqFrontMatterDataModificationBuildItem.SourceData source = new RoqFrontMatterDataModificationBuildItem.SourceData(
                null, "index.html", null, true,
                new io.vertx.core.json.JsonObject().put("title", "Home").put("description", "Desc"));

        OgImageTargetResolver.PageContext pageContext = OgImageTargetResolver.pageContextFromSource(source);
        assertThat(OgImageTargetResolver.matchesSource(config, source, pageContext.pagePath(), pageContext.collectionId(),
                pageContext.siteIndex())).isTrue();

        OgImageTarget target = OgImageTargetResolver.targetFromSource(config, source, pageContext.pagePath(),
                pageContext.collectionId(), pageContext.slug(), pageContext.siteIndex());
        assertThat(target).isNotNull();
        assertThat(target.pngPath()).isEqualTo("/og/index.png");
    }

    private static OgImageConfig config(String... keyValues) {
        SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder()
                .withMapping(OgImageConfig.class)
                .withValidateUnknown(false);
        for (String keyValue : keyValues) {
            int idx = keyValue.indexOf('=');
            builder.withDefaultValue(keyValue.substring(0, idx), keyValue.substring(idx + 1));
        }
        SmallRyeConfig config = builder.build();
        return config.getConfigMapping(OgImageConfig.class);
    }
}
