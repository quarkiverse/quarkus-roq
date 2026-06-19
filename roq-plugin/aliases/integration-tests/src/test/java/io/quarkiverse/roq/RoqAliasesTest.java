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
}
