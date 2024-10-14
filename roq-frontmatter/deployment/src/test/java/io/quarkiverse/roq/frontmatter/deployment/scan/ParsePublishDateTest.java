package io.quarkiverse.roq.frontmatter.deployment.scan;

import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanProcessor.parsePublishDate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.deployment.config.CollectionConfig;
import io.quarkiverse.roq.frontmatter.deployment.config.RoqFrontMatterConfig;
import io.vertx.core.json.JsonObject;

public class ParsePublishDateTest {

    @Test
    public void testDateInFilename() {
        // given
        Path path = Path.of("2004-09-07-title.md");
        JsonObject frontMatter = JsonObject.of();

        // when
        String publishDate = parsePublishDate(path, frontMatter, config);

        // then
        assertEquals("2004-09-07T12:00:00Z[GMT]", publishDate);
    }

    @Test
    public void testDateInFrontMatter() {
        // given
        Path path = Path.of("no-date-here.md");
        JsonObject frontMatter = JsonObject.of("date", "2020-10-13");

        // when
        String publishDate = parsePublishDate(path, frontMatter, config);

        // then
        assertEquals("2020-10-13T12:00:00Z[GMT]", publishDate);
    }

    @Test
    public void testDateTimeInFrontMatter() {
        // given
        Path path = Path.of("no-date-here.md");
        JsonObject frontMatter = JsonObject.of("date", "2020-10-13 13:10");

        // when
        String publishDate = parsePublishDate(path, frontMatter, config);

        // then
        assertEquals("2020-10-13T13:10:00Z[GMT]", publishDate);
    }

    @Test
    public void testDateInFuture() {
        // given
        Path path = Path.of("no-date-here.md");
        JsonObject frontMatter = JsonObject.of("date", "9999-12-31");

        // when
        String publishDate = parsePublishDate(path, frontMatter, config);

        // then
        assertNull(publishDate);
    }

    @Test
    public void testDateInFutureAllowed() {
        // given
        Path path = Path.of("no-date-here.md");
        JsonObject frontMatter = JsonObject.of("date", "9999-12-31");
        config.setFuture(true);

        // when
        String publishDate = parsePublishDate(path, frontMatter, config);

        // then
        assertEquals("9999-12-31T12:00:00Z[GMT]", publishDate);
    }

    private final TestConfig config = new TestConfig();

    static class TestConfig implements RoqFrontMatterConfig {
        private boolean future = false;

        @Override
        public List<String> includesDirs() {
            return List.of();
        }

        @Override
        public boolean generator() {
            return false;
        }

        public void setFuture(boolean future) {
            this.future = future;
        }

        @Override
        public boolean future() {
            return future;
        }

        @Override
        public String imagesPath() {
            return "";
        }

        @Override
        public boolean draft() {
            return false;
        }

        @Override
        public String dateFormat() {
            return "yyyy-MM-dd[ HH:mm][:ss][ Z]";
        }

        @Override
        public Optional<String> timeZone() {
            return Optional.of("GMT");
        }

        @Override
        public Map<String, CollectionConfig> collections() {
            return Map.of();
        }
    }
}
