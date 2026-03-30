package io.quarkiverse.roq.frontmatter.deployment.data;

import static io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataProcessor.parsePublishDate;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

/**
 * Pure unit tests (no Quarkus runtime).
 * <p>
 * Features tested: publish date parsing from filename pattern (YYYY-MM-DD-title.md),
 * from frontmatter 'date' field (date-only and datetime), timezone handling.
 */
@DisplayName("Roq FrontMatter - Publish date parsing")
public class ParsePublishDateTest {

    @Test
    public void testDateInFilename() {
        // given
        String path = "2004-09-07-title.md";
        JsonObject frontMatter = JsonObject.of();

        // when
        String publishDate = parsePublishDate(path, frontMatter, "yyyy-MM-dd[ HH:mm][:ss][ Z]", ZoneId.of("GMT"))
                .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        // then
        assertEquals("2004-09-07T00:00:00Z[GMT]", publishDate);
    }

    @Test
    public void testDateInFrontMatter() {
        // given
        String path = "no-date-here.md";
        JsonObject frontMatter = JsonObject.of("date", "2020-10-13");

        // when
        String publishDate = parsePublishDate(path, frontMatter, "yyyy-MM-dd[ HH:mm][:ss][ Z]", ZoneId.of("GMT"))
                .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        // then
        assertEquals("2020-10-13T00:00:00Z[GMT]", publishDate);
    }

    @Test
    public void testDateTimeInFrontMatter() {
        // given
        String path = "no-date-here.md";
        JsonObject frontMatter = JsonObject.of("date", "2020-10-13 13:10");

        // when
        String publishDate = parsePublishDate(path, frontMatter, "yyyy-MM-dd[ HH:mm][:ss][ Z]", ZoneId.of("GMT"))
                .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        // then
        assertEquals("2020-10-13T13:10:00Z[GMT]", publishDate);
    }

}
