package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterStep3DataProcessor.parsePublishDate;
import static org.junit.jupiter.api.Assertions.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqFrontMatterReadingException;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqSiteScanningException;
import io.vertx.core.json.JsonObject;

/**
 * Pure unit tests (no Quarkus runtime).
 * <p>
 * Features tested: publish date parsing from filename pattern (YYYY-MM-DD-title.md),
 * from frontmatter 'date' field (date-only and datetime), timezone handling,
 * fallback to now, null date in FM, invalid dates.
 */
@DisplayName("Roq FrontMatter - Publish date parsing")
public class ParsePublishDateTest {

    private static final String DEFAULT_DATE_FORMAT = "yyyy-M-d[ HH:mm][:ss][ Z]";
    private static final ZoneId GMT = ZoneId.of("GMT");

    @Test
    public void testDateInFilename() {
        String publishDate = parsePublishDate("2004-09-07-title.md", JsonObject.of(), DEFAULT_DATE_FORMAT, GMT)
                .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        assertEquals("2004-09-07T00:00:00Z[GMT]", publishDate);
    }

    @Test
    @DisplayName("Single-digit day in filename is parsed")
    public void testSingleDigitDayInFilename() {
        String publishDate = parsePublishDate("2024-10-9-title.md", JsonObject.of(), DEFAULT_DATE_FORMAT, GMT)
                .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        assertEquals("2024-10-09T00:00:00Z[GMT]", publishDate);
    }

    @Test
    @DisplayName("Single-digit month and day in filename is parsed")
    public void testSingleDigitMonthAndDayInFilename() {
        String publishDate = parsePublishDate("2024-3-5-title.md", JsonObject.of(), DEFAULT_DATE_FORMAT, GMT)
                .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        assertEquals("2024-03-05T00:00:00Z[GMT]", publishDate);
    }

    @Test
    public void testDateInFrontMatter() {
        String publishDate = parsePublishDate("no-date-here.md", JsonObject.of("date", "2020-10-13"),
                DEFAULT_DATE_FORMAT, GMT)
                .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        assertEquals("2020-10-13T00:00:00Z[GMT]", publishDate);
    }

    @Test
    public void testDateTimeInFrontMatter() {
        String publishDate = parsePublishDate("no-date-here.md", JsonObject.of("date", "2020-10-13 13:10"),
                DEFAULT_DATE_FORMAT, GMT)
                .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        assertEquals("2020-10-13T13:10:00Z[GMT]", publishDate);
    }

    @Test
    @DisplayName("Date with seconds in frontmatter")
    public void testDateTimeWithSecondsInFrontMatter() {
        String publishDate = parsePublishDate("no-date-here.md", JsonObject.of("date", "2020-10-13 13:10:45"),
                DEFAULT_DATE_FORMAT, GMT)
                .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        assertEquals("2020-10-13T13:10:45Z[GMT]", publishDate);
    }

    @Test
    @DisplayName("Frontmatter date takes precedence over filename date")
    public void testFrontMatterDateOverridesFilename() {
        String publishDate = parsePublishDate("2004-09-07-title.md", JsonObject.of("date", "2020-10-13"),
                DEFAULT_DATE_FORMAT, GMT)
                .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        assertEquals("2020-10-13T00:00:00Z[GMT]", publishDate);
    }

    @Test
    @DisplayName("No date anywhere returns null")
    public void testNoDateReturnsNull() {
        ZonedDateTime result = parsePublishDate("no-date-here.md", JsonObject.of(), DEFAULT_DATE_FORMAT, GMT);
        assertNull(result, "Pages with no date should return null");
    }

    @Test
    @DisplayName("Null date value in frontmatter falls back to filename or now")
    public void testNullDateValueInFrontMatter() {
        // date key exists but value is null — should fall back to filename pattern or now
        JsonObject fm = new JsonObject().put("date", (String) null);
        ZonedDateTime result = parsePublishDate("2024-05-10-post.md", fm, DEFAULT_DATE_FORMAT, GMT);

        assertEquals("2024-05-10T00:00:00Z[GMT]", result.format(DateTimeFormatter.ISO_ZONED_DATE_TIME));
    }

    @Test
    @DisplayName("Null date value in frontmatter with no filename date returns null")
    public void testNullDateValueNoFilenameReturnsNull() {
        JsonObject fm = new JsonObject().put("date", (String) null);
        ZonedDateTime result = parsePublishDate("no-date-here.md", fm, DEFAULT_DATE_FORMAT, GMT);
        assertNull(result, "Null FM date with no filename date should return null");
    }

    @Test
    @DisplayName("Filename date uses configured timezone")
    public void testFilenameDateUsesTimezone() {
        ZoneId paris = ZoneId.of("Europe/Paris");
        ZonedDateTime result = parsePublishDate("2024-06-15-post.md", JsonObject.of(), DEFAULT_DATE_FORMAT, paris);

        assertEquals("Europe/Paris", result.getZone().getId());
        assertEquals("2024-06-15T00:00:00+02:00[Europe/Paris]", result.format(DateTimeFormatter.ISO_ZONED_DATE_TIME));
    }

    @Test
    @DisplayName("Frontmatter date uses configured timezone")
    public void testFrontMatterDateUsesTimezone() {
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        ZonedDateTime result = parsePublishDate("no-date.md", JsonObject.of("date", "2024-01-15 14:30"),
                DEFAULT_DATE_FORMAT, tokyo);

        assertEquals("Asia/Tokyo", result.getZone().getId());
        assertEquals("2024-01-15T14:30:00+09:00[Asia/Tokyo]", result.format(DateTimeFormatter.ISO_ZONED_DATE_TIME));
    }

    @Test
    @DisplayName("Configured timezone overrides explicit offset in frontmatter")
    public void testConfiguredTimezoneOverridesExplicitOffset() {
        ZonedDateTime result = parsePublishDate("no-date.md", JsonObject.of("date", "2024-06-15 10:00 +0530"),
                DEFAULT_DATE_FORMAT, GMT);

        // The configured timezone (GMT) takes precedence over the offset in the date string
        assertEquals("GMT", result.getZone().getId());
    }

    @Test
    @DisplayName("Invalid date in frontmatter throws RoqFrontMatterReadingException")
    public void testInvalidDateInFrontMatter() {
        assertThrows(RoqFrontMatterReadingException.class,
                () -> parsePublishDate("no-date.md", JsonObject.of("date", "not-a-date"), DEFAULT_DATE_FORMAT, GMT));
    }

    @Test
    @DisplayName("Invalid date in filename throws RoqSiteScanningException")
    public void testInvalidDateInFilename() {
        assertThrows(RoqSiteScanningException.class,
                () -> parsePublishDate("9999-99-99-post.md", JsonObject.of(), DEFAULT_DATE_FORMAT, GMT));
    }

}
