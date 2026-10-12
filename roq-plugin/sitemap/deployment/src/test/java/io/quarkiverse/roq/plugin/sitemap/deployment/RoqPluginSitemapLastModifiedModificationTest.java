package io.quarkiverse.roq.plugin.sitemap.deployment;

import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.LAST_MODIFIED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem.SourceData;
import io.vertx.core.json.JsonObject;

/**
 * Features tested: which pages get a computed {@code last-modified-at}, and its format.
 */
public class RoqPluginSitemapLastModifiedModificationTest {

    private static final Instant DATE = Instant.parse("2026-03-01T10:00:00Z");
    private static final Path PATH = Path.of("content/page.md").toAbsolutePath();

    private final List<Path> lookedUp = new ArrayList<>();

    @Test
    void setsTheDateInTheSiteTimeZone() {
        final JsonObject fm = this.modify(true, new JsonObject());

        assertThat(fm.getString(LAST_MODIFIED_AT)).isEqualTo("2026-03-01T11:00:00+01:00[Europe/Paris]");
        assertThat(this.lookedUp).containsExactly(PATH);
    }

    @Test
    void skipsPagesExcludedFromTheSitemap() {
        final JsonObject fm = this.modify(true, new JsonObject().put("sitemap", false));

        assertThat(fm.containsKey(LAST_MODIFIED_AT)).isFalse();
        assertThat(this.lookedUp).isEmpty();
    }

    @Test
    void keepsAnExplicitDate() {
        final JsonObject fm = this.modify(true, new JsonObject().put(LAST_MODIFIED_AT, "2020-01-01T00:00:00Z"));

        assertThat(fm.getString(LAST_MODIFIED_AT)).isEqualTo("2020-01-01T00:00:00Z");
        assertThat(this.lookedUp).isEmpty();
    }

    @Test
    void skipsLayouts() {
        final JsonObject fm = this.modify(false, new JsonObject());

        assertThat(fm.containsKey(LAST_MODIFIED_AT)).isFalse();
        assertThat(this.lookedUp).isEmpty();
    }

    @Test
    void skipsWhenNoDateIsFound() {
        final JsonObject fm = RoqPluginSitemapProcessor.lastModifiedModification(ZoneId.of("UTC"), path -> null)
                .modifier().modify(new SourceData(PATH, "page.md", null, true, new JsonObject()));

        assertThat(fm.containsKey(LAST_MODIFIED_AT)).isFalse();
    }

    private JsonObject modify(final boolean isPage, final JsonObject fm) {
        return RoqPluginSitemapProcessor.lastModifiedModification(ZoneId.of("Europe/Paris"), path -> {
            this.lookedUp.add(path);
            return DATE;
        }).modifier().modify(new SourceData(PATH, "page.md", null, isPage, fm));
    }
}
