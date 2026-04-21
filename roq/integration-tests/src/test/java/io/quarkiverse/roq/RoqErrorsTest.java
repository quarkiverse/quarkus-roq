package io.quarkiverse.roq;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class RoqErrorsTest extends AbstractRoqTest {

    @Test
    public void testErrorFilePage() {
        RestAssured.when().get("/posts/error-file").then().statusCode(500).log().ifValidationFails().body(allOf(
                containsString("We couldn't render this page"),
                containsString("Show technical details"),
                containsString("RoqStaticFileException"),
                containsString("Cannot attach file 'not-found.pdf' to this page"),
                containsString("Only directory pages with an index can have attached files")));
    }

    @Test
    public void testErrorImagePage() {
        RestAssured.when().get("/posts/error-image").then().statusCode(500).log().ifValidationFails().body(allOf(
                containsString("We couldn't render this page"),
                containsString("Show technical details"),
                containsString("RoqStaticFileException"),
                containsString("'images/not-found.png' not found in the public directory")));
    }

    @Test
    public void testErrorImagePageDir() {
        RestAssured.when().get("/posts/error-image-dir").then().statusCode(500).log().ifValidationFails().body(allOf(
                containsString("We couldn't render this page"),
                containsString("Show technical details"),
                containsString("'images/not-found.png' not found in the public directory")));
    }

    @Test
    public void testErrorFilePageDir() {
        RestAssured.when().get("/posts/error-file-dir").then().statusCode(500).log().ifValidationFails().body(allOf(
                containsString("We couldn't render this page"),
                containsString("Show technical details"),
                containsString("'not-found.pdf' not found in the 'posts/error-file-dir' directory (directory is empty)")));
    }

    @Test
    public void testErrorFilePageDirNotFound() {
        RestAssured.when().get("/posts/error-image-not-found").then().statusCode(500).log().ifValidationFails()
                .body(allOf(
                        containsString("We couldn't render this page"),
                        containsString("Show technical details"),
                        containsString("'not-found.png' not found in the 'posts/error-image-not-found' directory"),
                        containsString("Available files: hello.png")));
    }

    @Test
    public void testErrorImageSite() {
        RestAssured.when().get("/error-image-site").then().statusCode(500).log().ifValidationFails().body(allOf(
                containsString("We couldn't render this page"),
                containsString("Show technical details"),
                containsString("'images/not-found.png' not found in the public directory")));
    }

    @Test
    public void testErrorStaticFileSite() {
        RestAssured.when().get("/error-static-file").then().statusCode(500).log().ifValidationFails()
                .body(allOf(
                        containsString("We couldn't render this page"),
                        containsString("Show technical details"),
                        containsString("'not-found.pdf' not found in the public directory")));
    }

}