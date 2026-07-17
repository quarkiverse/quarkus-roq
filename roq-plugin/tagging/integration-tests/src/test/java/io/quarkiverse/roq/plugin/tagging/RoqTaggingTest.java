package io.quarkiverse.roq.plugin.tagging;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        @Test
        public void testSiteTagsHaveNoDuplicates() {
            // Verify that tags list has no duplicates
            // Count the number of tags and verify it matches expected unique count
            String response = when().get("/tags-test/")
                    .then()
                    .statusCode(200)
                    .log().ifValidationFails()
                    .extract().asString();

            // Count occurrences of data-tag attributes
            // Expected tags: java, quarkus, roq, jvm, cloud-native, static-site, bunnies, some, topic, cloud, native
            // Each tag should appear exactly once in the tags list
            long javaCount = countOccurrences(response, "data-tag=\"java\"");
            long quarkusCount = countOccurrences(response, "data-tag=\"quarkus\"");
            long roqCount = countOccurrences(response, "data-tag=\"roq\"");

            // Each tag should appear exactly once
            assertEquals(1, javaCount, "Expected 'java' tag to appear once");
            assertEquals(1, quarkusCount, "Expected 'quarkus' tag to appear once");
            assertEquals(1, roqCount, "Expected 'roq' tag to appear once");
        }

        private long countOccurrences(String text, String substring) {
            int count = 0;
            int index = 0;
            while ((index = text.indexOf(substring, index)) != -1) {
                count++;
                index += substring.length();
            }
            return count;
        }

        @Test
        public void testSiteTagsCountsPagesCorrectly() {
            // Verify page counts for each tag
            // Note: Now we have additional posts with mixed case tags
            // post-mixed-case.md has: AI (separate), Java (separate), DevOps (separate), normal
            // post-lowercase.md has: ai (separate), java (separate), devops (separate), normal
            when().get("/tags-test/")
                    .then()
                    .statusCode(200)
                    .log().ifValidationFails()
                    // java tag appears in: post-java.md, post-quarkus.md, post-roq.md, post-lowercase.md (4 posts)
                    // Java tag (uppercase) appears in: post-mixed-case.md (1 post)
                    .body(containsString("data-tag=\"java\"")) // at least exists
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

    @Nested
    class TagCaseSensitivityDefaultTest {
        /**
         * When quarkus.roq.tagging.lowercase is NOT set (default = false),
         * tags should preserve their case, so 'AI' and 'ai' should be treated as different tags.
         * This is the CORRECT and EXPECTED behavior for case-sensitive mode.
         */
        @Test
        public void testTagsAreCaseSensitiveByDefault() {
            String response = when().get("/case-test/")
                    .then()
                    .statusCode(200)
                    .log().ifValidationFails()
                    .extract().asString();

            // EXPECTED: With case-sensitive mode (lowercase=false), both 'AI' and 'ai' exist as SEPARATE tags
            // post-mixed-case.md has: AI, Java, DevOps, normal
            // post-lowercase.md has: ai, java, devops, normal
            // Slugification preserves case by default, so we get: AI, ai, Java, java, DevOps, devops, normal (17 total tags)

            assertTrue(response.contains("id=\"ai-uppercase-exists\">YES"), "'AI' tag should be preserved");
            assertTrue(response.contains("id=\"ai-lowercase-exists\">YES"), "'ai' tag should be separate");
            assertTrue(response.contains("id=\"java-uppercase-exists\">YES"), "'Java' tag should be preserved");
            assertTrue(response.contains("id=\"java-lowercase-exists\">YES"), "'java' tag should be separate");
            assertTrue(response.contains("id=\"devops-mixed-exists\">YES"), "'DevOps' tag should be preserved");
            assertTrue(response.contains("id=\"devops-lowercase-exists\">YES"), "'devops' tag should be separate");
            assertTrue(response.contains("id=\"normal-exists\">YES"), "'normal' tag should exist");

            // Both uppercase and lowercase versions should exist
            assertTrue(response.contains("data-tag=\"AI\""), "Expected to find 'AI' tag");
            assertTrue(response.contains("data-tag=\"ai\""), "Expected to find 'ai' tag");
            assertTrue(response.contains("data-tag=\"Java\""), "Expected to find 'Java' tag");
            assertTrue(response.contains("data-tag=\"java\""), "Expected to find 'java' tag");
            assertTrue(response.contains("data-tag=\"DevOps\""), "Expected to find 'DevOps' tag");
            assertTrue(response.contains("data-tag=\"devops\""), "Expected to find 'devops' tag");
            assertTrue(response.contains("data-tag=\"normal\""), "Expected to find 'normal' tag");
        }

        @Test
        public void testTagCountsIncludeBothCases() {
            String response = when().get("/case-test/")
                    .then()
                    .statusCode(200)
                    .log().ifValidationFails()
                    .extract().asString();

            // EXPECTED: Since case is preserved, we have SEPARATE counts for each case variant
            // 'ai' (lowercase) should appear independently
            assertTrue(response.contains("data-tag=\"ai\""), "Expected to find 'ai' tag");
            // 'AI' (uppercase) should appear independently
            assertTrue(response.contains("data-tag=\"AI\""), "Expected to find 'AI' tag");

            // 'devops' and 'DevOps' are separate
            assertTrue(response.contains("data-tag=\"devops\""), "Expected to find 'devops' tag");
            assertTrue(response.contains("data-tag=\"DevOps\""), "Expected to find 'DevOps' tag");

            // 'normal' should exist (only lowercase version in posts)
            assertTrue(response.contains("data-tag=\"normal\""), "Expected to find 'normal' tag");

            // Total unique tags is 17 (including both case variants)
            // Tags: ai, AI, java, Java, devops, DevOps, normal, quarkus, roq, jvm, cloud-native, static-site, bunnies, some, topic, cloud, native
            assertTrue(response.contains("Total unique tags: 17"), "Expected to find 17 unique tags");
        }

        @Test
        public void testNoDuplicateTagsInListButCaseVariantsExist() {
            String response = when().get("/case-test/")
                    .then()
                    .statusCode(200)
                    .log().ifValidationFails()
                    .extract().asString();

            // Each case variant should appear exactly once (no duplicates of the SAME case)
            long aiLowerCount = countOccurrences(response, "data-tag=\"ai\"");
            long aiUpperCount = countOccurrences(response, "data-tag=\"AI\"");
            long javaLowerCount = countOccurrences(response, "data-tag=\"java\"");
            long javaUpperCount = countOccurrences(response, "data-tag=\"Java\"");
            long devopsLowerCount = countOccurrences(response, "data-tag=\"devops\"");
            long devopsMixedCount = countOccurrences(response, "data-tag=\"DevOps\"");
            long normalCount = countOccurrences(response, "data-tag=\"normal\"");

            assertEquals(1, aiLowerCount, "Expected 'ai' tag to appear once");
            assertEquals(1, aiUpperCount, "Expected 'AI' tag to appear once");
            assertEquals(1, javaLowerCount, "Expected 'java' tag to appear once");
            assertEquals(1, javaUpperCount, "Expected 'Java' tag to appear once");
            assertEquals(1, devopsLowerCount, "Expected 'devops' tag to appear once");
            assertEquals(1, devopsMixedCount, "Expected 'DevOps' tag to appear once");
            assertEquals(1, normalCount, "Expected 'normal' tag to appear once");
        }

        private long countOccurrences(String text, String substring) {
            int count = 0;
            int index = 0;
            while ((index = text.indexOf(substring, index)) != -1) {
                count++;
                index += substring.length();
            }
            return count;
        }
    }
}
