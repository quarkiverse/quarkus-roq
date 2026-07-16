package io.quarkiverse.roq.plugin.tagging;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Tests for the tagging plugin with quarkus.roq.tagging.lowercase=true.
 * When this property is set, all tags should be converted to lowercase,
 * so 'AI' and 'ai' should be treated as the same tag 'ai'.
 */
@QuarkusTest
@TestProfile(RoqTaggingLowercaseTest.LowercaseProfile.class)
public class RoqTaggingLowercaseTest {

    public static class LowercaseProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.roq.tagging.lowercase", "true");
        }
    }

    @Test
    public void testTagsAreLowercasedWhenPropertySet() {
        String response = when().get("/case-test/")
                .then()
                .statusCode(200)
                .log().ifValidationFails()
                .extract().asString();

        // EXPECTED: With lowercase=true, both 'AI' and 'ai' should be converted to 'ai'
        // post-mixed-case.md has: AI, Java, DevOps, normal
        // post-lowercase.md has: ai, java, devops, normal
        // All should be lowercased to: ai, java, devops, normal

        // Only lowercase versions should exist
        assertTrue(response.contains("id=\"ai-uppercase-exists\">NO"), "AI uppercase tag should NOT exist");
        assertTrue(response.contains("id=\"ai-lowercase-exists\">YES"), "ai lowercase tag should exist");
        assertTrue(response.contains("id=\"java-uppercase-exists\">NO"), "Java uppercase tag should NOT exist");
        assertTrue(response.contains("id=\"java-lowercase-exists\">YES"), "java lowercase tag should exist");
        assertTrue(response.contains("id=\"devops-mixed-exists\">NO"), "DevOps mixed case tag should NOT exist");
        assertTrue(response.contains("id=\"devops-lowercase-exists\">YES"), "devops lowercase tag should exist");
        assertTrue(response.contains("id=\"normal-exists\">YES"), "normal tag should exist");
    }

    @Test
    public void testTagCountsAreMergedWhenLowercased() {
        String response = when().get("/case-test/")
                .then()
                .statusCode(200)
                .log().ifValidationFails()
                .extract().asString();

        // EXPECTED: 'ai' should have all posts (AI and ai merged)
        assertTrue(response.contains("data-tag=\"ai\" data-count=\"2\""), "Expected ai tag with count 2");

        // EXPECTED: 'devops' should have all posts (DevOps and devops merged)
        assertTrue(response.contains("data-tag=\"devops\" data-count=\"2\""), "Expected devops tag with count 2");

        // 'normal' should have 2 posts (same case, so same as without lowercase)
        assertTrue(response.contains("data-tag=\"normal\" data-count=\"2\""), "Expected normal tag with count 2");

        // EXPECTED: Uppercase variants should NOT exist
        assertFalse(response.contains("data-tag=\"AI\""), "AI tag should NOT exist with lowercase=true");
        assertFalse(response.contains("data-tag=\"Java\""), "Java tag should NOT exist with lowercase=true");
        assertFalse(response.contains("data-tag=\"DevOps\""), "DevOps tag should NOT exist with lowercase=true");
    }

    @Test
    public void testTotalTagCountIsReducedWithLowercase() {
        String response = when().get("/case-test/")
                .then()
                .statusCode(200)
                .log().ifValidationFails()
                .extract().asString();

        // EXPECTED: With lowercase=true, the total should be REDUCED (case variants merged)
        // Tags after lowercasing: ai, java, devops, normal, quarkus, roq, jvm, cloud-native, static-site, bunnies, some, topic, cloud, native
        // Total = 14 unique tags (AI→ai, Java→java, DevOps→devops merged)
        assertTrue(response.contains("Total unique tags: 14"), "Expected 14 unique tags with lowercase=true");
    }

    @Test
    public void testNoDuplicateTagsWithLowercase() {
        String response = when().get("/case-test/")
                .then()
                .statusCode(200)
                .log().ifValidationFails()
                .extract().asString();

        // EXPECTED: Only lowercase tags should appear, each exactly once
        long aiLowerCount = countOccurrences(response, "data-tag=\"ai\"");
        long aiUpperCount = countOccurrences(response, "data-tag=\"AI\"");
        long javaLowerCount = countOccurrences(response, "data-tag=\"java\"");
        long javaUpperCount = countOccurrences(response, "data-tag=\"Java\"");
        long devopsLowerCount = countOccurrences(response, "data-tag=\"devops\"");
        long devopsMixedCount = countOccurrences(response, "data-tag=\"DevOps\"");
        long normalCount = countOccurrences(response, "data-tag=\"normal\"");

        assertEquals(1, aiLowerCount, "'ai' tag should appear exactly once");
        assertEquals(0, aiUpperCount, "'AI' tag should NOT exist with lowercase=true");
        assertEquals(1, javaLowerCount, "'java' tag should appear exactly once");
        assertEquals(0, javaUpperCount, "'Java' tag should NOT exist with lowercase=true");
        assertEquals(1, devopsLowerCount, "'devops' tag should appear exactly once");
        assertEquals(0, devopsMixedCount, "'DevOps' tag should NOT exist with lowercase=true");
        assertEquals(1, normalCount, "'normal' tag should appear exactly once");
    }

    @Test
    public void testTagPagesAreLowercased() {
        // Tag pages should be accessible via lowercase tag names
        when().get("/posts/tag/ai")
                .then()
                .statusCode(200)
                .log().ifValidationFails()
                .body(containsString("Mixed Case Tags Post"))
                .body(containsString("Lowercase Tags Post"));

        when().get("/posts/tag/devops")
                .then()
                .statusCode(200)
                .log().ifValidationFails()
                .body(containsString("Mixed Case Tags Post"))
                .body(containsString("Lowercase Tags Post"));

        when().get("/posts/tag/java")
                .then()
                .statusCode(200)
                .log().ifValidationFails()
                .body(containsString("Java Post"))
                .body(containsString("Mixed Case Tags Post"))
                .body(containsString("Lowercase Tags Post"));
    }

    @Test
    public void testUppercaseTagPagesDoNotExist() {
        // Uppercase tag pages should not exist when lowercase=true
        // Note: 'AI' gets slugified to 'ai' first, so this test might not be meaningful
        // but we're testing the principle
        when().get("/posts/tag/AI")
                .then()
                .statusCode(404);

        when().get("/posts/tag/DevOps")
                .then()
                .statusCode(404);
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
