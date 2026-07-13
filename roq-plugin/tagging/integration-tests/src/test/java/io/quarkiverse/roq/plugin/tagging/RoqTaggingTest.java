package io.quarkiverse.roq.plugin.tagging;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class RoqTaggingTest {

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

    @Test
    public void testSiteTagsCountsPagesCorrectly() {
        // Verify page counts for each tag
        // java: 3 posts (post-java, post-quarkus, post-roq)
        // quarkus: 1 post
        // roq: 1 post
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
