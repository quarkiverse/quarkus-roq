package io.quarkiverse.roq;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class RoqErrorsTest extends AbstractRoqTest {

    @Test
    public void testDefaultTheme404Page() {
        RestAssured.when().get("/404.html").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Oops! Roq is saying 404 - Hello, world! I'm Roq"))
                .body("html.body.@class", equalTo("page-not-found"))
                .body(containsString("<div class=\"main\">"));
    }

    @Test
    public void testErrorFilePage() {
        RestAssured.when().get("/posts/error-file").then().statusCode(500).log().ifValidationFails().body(containsString(
                "Cannot attach file &#39;not-found.pdf&#39; to this page."));
    }

    @Test
    public void testErrorImagePage() {
        RestAssured.when().get("/posts/error-image").then().statusCode(500).log().ifValidationFails().body(containsString(
                "&#39;images/not-found.png&#39; not found in the public directory"));
    }

    @Test
    public void testErrorImagePageDir() {
        RestAssured.when().get("/posts/error-image-dir").then().statusCode(500).log().ifValidationFails().body(containsString(
                "&#39;images/not-found.png&#39; not found in the public directory"));
    }

    @Test
    public void testErrorFilePageDir() {
        RestAssured.when().get("/posts/error-file-dir").then().statusCode(500).log().ifValidationFails().body(containsString(
                "&#39;not-found.pdf&#39; not found in"));
    }

    @Test
    public void testErrorFilePageDirNotFound() {
        RestAssured.when().get("/posts/error-image-not-found").then().statusCode(500).log().ifValidationFails()
                .body(containsString(
                        "&#39;not-found.png&#39; not found in"));
    }

    @Test
    public void testErrorImageSite() {
        RestAssured.when().get("/error-image-site").then().statusCode(500).log().ifValidationFails().body(containsString(
                "&#39;images/not-found.png&#39; not found in the public directory"));
    }

    @Test
    public void testErrorStaticFileSite() {
        RestAssured.when().get("/error-static-file").then().statusCode(500).log().ifValidationFails()
                .body(containsString("&#39;not-found.pdf&#39; not found in the public directory"));
    }

}
