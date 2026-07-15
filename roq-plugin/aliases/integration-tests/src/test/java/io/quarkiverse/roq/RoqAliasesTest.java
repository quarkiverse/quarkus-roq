package io.quarkiverse.roq;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class RoqAliasesTest {

    @Test
    public void testAliasWithTrailingSlash() {
        RestAssured.when().get("/old-post-url/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Redirecting"));
    }

    @Test
    public void testAliasWithoutTrailingSlash() {
        RestAssured.when().get("/old-post-url").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Redirecting"));
    }

    @Test
    public void testNestedAliasWithTrailingSlash() {
        RestAssured.when().get("/legacy/nested-path/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Redirecting"));
    }

    @Test
    public void testNestedAliasWithoutTrailingSlash() {
        RestAssured.when().get("/legacy/nested-path").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Redirecting"));
    }

    @Test
    public void testAliasesKeyWithTrailingSlash() {
        RestAssured.when().get("/another-alias/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Redirecting"));
    }

    @Test
    public void testAliasesKeyWithoutTrailingSlash() {
        RestAssured.when().get("/another-alias").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Redirecting"));
    }

    @Test
    public void testMetaRefreshTagHasCorrectQuoting() {
        // Regression test: meta refresh URL must not use double quotes (causes unbalanced quotes)
        // Bug was: content="0; url="{url}"
        //   After substitution: content="0; url="https://..." - second " prematurely closes content attribute
        // Fix is: content="0; url='{url}'"
        //   After substitution: content="0; url='https://...'" - properly balanced
        String body = RestAssured.when().get("/old-post-url/").then().statusCode(200).extract().asString();

        // Verify meta refresh tag exists
        org.junit.jupiter.api.Assertions.assertTrue(
                body.contains("http-equiv=\"refresh\""),
                "Should have meta refresh tag");

        // Extract the meta refresh line
        String metaRefreshLine = body.lines()
                .filter(line -> line.contains("http-equiv=\"refresh\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Meta refresh tag not found"));

        // Regression check: ensure URL is not wrapped in double quotes (the bug pattern)
        // Bug pattern: url="..." causes unbalanced quotes (second " closes the content attribute early)
        // Correct pattern: url='...' (single quotes, properly balanced)
        org.junit.jupiter.api.Assertions.assertFalse(
                metaRefreshLine.matches(".*url=\"[^>]*\".*"),
                "URL should NOT be wrapped in double quotes (causes unbalanced quotes). Actual line: " + metaRefreshLine);
    }
}
