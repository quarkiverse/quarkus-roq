package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterStep3DataProcessor.parsePublishDate;
import static org.junit.jupiter.api.Assertions.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

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

    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd[ HH:mm][:ss][ Z]";
    private static final ZoneId GMT = ZoneId.of("GMT");

    @Test
    public void testDateInFilename() {
        String publishDate = parsePublishDate("2004-09-07-title.md", JsonObject.of(), DEFAULT_DATE_FORMAT, GMT)
                .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        assertEquals("2004-09-07T00:00:00Z[GMT]", publishDate);
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
    @DisplayName("No date anywhere falls back to now")
    public void testNoDateFallsBackToNow() {
        ZonedDateTime before = ZonedDateTime.now(GMT).truncatedTo(ChronoUnit.SECONDS);
        ZonedDateTime result = parsePublishDate("no-date-here.md", JsonObject.of(), DEFAULT_DATE_FORMAT, GMT);
        ZonedDateTime after = ZonedDateTime.now(GMT).truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);

        assertNotNull(result);
        assertFalse(result.isBefore(before), "Fallback date should not be before test start");
        assertFalse(result.isAfter(after), "Fallback date should not be after test end");
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
    @DisplayName("Null date value in frontmatter with no filename date falls back to now")
    public void testNullDateValueNoFilenameFallsBackToNow() {
        JsonObject fm = new JsonObject().put("date", (String) null);
        ZonedDateTime before = ZonedDateTime.now(GMT).truncatedTo(ChronoUnit.SECONDS);
        ZonedDateTime result = parsePublishDate("no-date-here.md", fm, DEFAULT_DATE_FORMAT, GMT);
        ZonedDateTime after = ZonedDateTime.now(GMT).truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);

        assertNotNull(result);
        assertFalse(result.isBefore(before));
        assertFalse(result.isAfter(after));
    }

    @Test
    @DisplayName("Different timezone is applied")
    public void testTimezoneApplied() {
        ZoneId paris = ZoneId.of("Europe/Paris");
        ZonedDateTime result = parsePublishDate("2024-06-15-post.md", JsonObject.of(), DEFAULT_DATE_FORMAT, paris);

        assertEquals("Europe/Paris", result.getZone().getId());
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
