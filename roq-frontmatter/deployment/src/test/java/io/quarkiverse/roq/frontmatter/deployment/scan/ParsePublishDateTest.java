package io.quarkiverse.roq.frontmatter.deployment.scan;

import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanProcessor.parsePublishDate;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

public class ParsePublishDateTest {

    @Test
    public void testDateInFilename() {
        // given
        Path path = Path.of("2004-09-07-title.md");
        JsonObject frontMatter = JsonObject.of();

        // when
        String publishDate = parsePublishDate(path, frontMatter, "yyyy-MM-dd[ HH:mm][:ss][ Z]", Optional.of("GMT"))
                .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        // then
        assertEquals("2004-09-07T12:00:00Z[GMT]", publishDate);
    }

    @Test
    public void testDateInFrontMatter() {
        // given
        Path path = Path.of("no-date-here.md");
        JsonObject frontMatter = JsonObject.of("date", "2020-10-13");

        // when
        String publishDate = parsePublishDate(path, frontMatter, "yyyy-MM-dd[ HH:mm][:ss][ Z]", Optional.of("GMT"))
                .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        // then
        assertEquals("2020-10-13T12:00:00Z[GMT]", publishDate);
    }

    @Test
    public void testDateTimeInFrontMatter() {
        // given
        Path path = Path.of("no-date-here.md");
        JsonObject frontMatter = JsonObject.of("date", "2020-10-13 13:10");

        // when
        String publishDate = parsePublishDate(path, frontMatter, "yyyy-MM-dd[ HH:mm][:ss][ Z]", Optional.of("GMT"))
                .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        // then
        assertEquals("2020-10-13T13:10:00Z[GMT]", publishDate);
    }

}
