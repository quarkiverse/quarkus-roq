package io.quarkiverse.roq.plugin.tagging;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class RoqTaggingTest {

    @Test
    public void testGeneratedTagPage() {
        // site.tags should return a map with all tags from all pages
        when().get("/posts/tag/quarkus")
                .then()
                .statusCode(200)
                .log().ifValidationFails()
                .body(containsString("Quarkus Post"));
    }

    @Test
    public void testGeneratedTagPageForTagWithHyphen() {
        // site.tags should return a map with all tags from all pages
        when().get("/posts/tag/cloud-native")
                .then()
                .statusCode(200)
                .log().ifValidationFails()
                .body(containsString("Quarkus Post"));
    }

    /**
     * If a tag has a space, the expected behaviour is that it gets split, so "some topic" becomes "some" and "topic" tags.
     */
    @Test
    public void testGeneratedTagPageForTagWithSpace() {
        // site.tags should return a map with all tags from all pages
        when().get("/posts/tag/some")
                .then()
                .statusCode(200)
                .log().ifValidationFails()
                .body(containsString("Quarkus Post"));

        when().get("/posts/tag/some-topic")
                .then()
                .statusCode(404);
    }

    @Nested
    class SiteTagsAggregationTest {
        @Test
        public void testSiteTagsReturnsAllTags() {
            // site.tags should return a map with all tags from all pages
            when().get("/tags-test/")
                    .then()
                    .statusCode(200)
                    .log().ifValidationFails()
                    .body(containsString("data-tag=\"java\""))
                    .body(containsString("data-tag=\"quarkus\""))
                    .body(containsString("data-tag=\"roq\""))
                    .body(containsString("data-tag=\"jvm\""))
                    .body(containsString("data-tag=\"cloud-native\""))
                    .body(containsString("data-tag=\"static-site\""));
        }

        @Disabled("See https://github.com/quarkiverse/quarkus-roq/issues/1086. When a tagging template is present, a page gets n extra copies in Site.allPages, where n is the number of tags it has.")
        @Test
        public void testSiteTagsCountsPagesCorrectly() {
            // Verify page counts for each tag
            when().get("/tags-test/")
                    .then()
                    .statusCode(200)
                    .log().ifValidationFails()
                    .body(containsString("data-tag=\"java\" data-count=\"3\""))
                    .body(containsString("data-tag=\"quarkus\" data-count=\"1\""))
                    .body(containsString("data-tag=\"roq\" data-count=\"1\""));
        }

        @Test
        public void testSiteTagsGetReturnsSpecificTagPages() {
            // site.tags.get('java') should return all pages with java tag
            when().get("/tags-test/")
                    .then()
                    .statusCode(200)
                    .log().ifValidationFails()
                    .body(containsString("<ul id=\"java-pages\">"))
                    .body(containsString("<li>Java Post</li>"))
                    .body(containsString("<li>Quarkus Post</li>"))
                    .body(containsString("<li>Roq Post</li>"))
                    .body(not(containsString("<li>Post Without Tags</li>")));
        }

        @Test
        public void testTagsAreSlugified() {
            // Tags should be slugified (lowercase, hyphens)
            // "Cloud Native" or "cloud_native" -> "cloud-native"
            when().get("/tags-test/")
                    .then()
                    .statusCode(200)
                    .log().ifValidationFails()
                    .body(containsString("data-tag=\"cloud-native\""))
                    .body(containsString("data-tag=\"static-site\""));
        }

        @Test
        public void testCommaSeparatedTagsWork() {
            // Tags from post-quarkus.md: "quarkus, java, cloud-native"
            // Should be parsed correctly
            when().get("/tags-test/")
                    .then()
                    .statusCode(200)
                    .log().ifValidationFails()
                    .body(containsString("data-tag=\"quarkus\""))
                    .body(containsString("data-tag=\"cloud-native\""));
        }

        @Test
        public void testTagsWithSpacesDoNotCauseErrors() {
            // At the moment, tags with spaces get split; this confirms that splitting happens, rather than an error
            when().get("/tags-test")
                    .then()
                    .statusCode(200)
                    .log().ifValidationFails()
                    .body(containsString("data-tag=\"quarkus\""))
                    .body(containsString("data-tag=\"cloud\""))
                    .body(containsString("data-tag=\"native\""))
                    .body(containsString("data-tag=\"some\""))
                    .body(containsString("data-tag=\"topic\""));
        }

        @Test
        public void testListFormatTagsWork() {
            // Tags from post-java.md and post-roq.md use YAML list format
            // Should be parsed correctly
            when().get("/tags-test/")
                    .then()
                    .statusCode(200)
                    .log().ifValidationFails()
                    .body(containsString("data-tag=\"jvm\""))
                    .body(containsString("data-tag=\"static-site\""));
        }
    }
}
