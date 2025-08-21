package io.quarkiverse.roq;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class RoqErrorsTest extends AbstractRoqTest {

    @Test
    public void testErrorFilePage() {
        RestAssured.when().get("/posts/error-file").then().statusCode(500).log().ifValidationFails().body(containsString(
                "RoqStaticFileException: Only page directories with an index can have attached files. Then add 'not-found.pdf' to the page directory to fix this error."));
    }

    @Test
    public void testErrorImagePage() {
        RestAssured.when().get("/posts/error-image").then().statusCode(500).log().ifValidationFails().body(containsString(
                "RoqStaticFileException: File 'images/not-found.png' not found in public dir"));
    }

    @Test
    public void testErrorImagePageDir() {
        RestAssured.when().get("/posts/error-image-dir").then().statusCode(500).log().ifValidationFails().body(containsString(
                "File 'images/not-found.png' not found in public dir"));
    }

    @Test
    public void testErrorFilePageDir() {
        RestAssured.when().get("/posts/error-file-dir").then().statusCode(500).log().ifValidationFails().body(containsString(
                "Can't find 'not-found.pdf' in  'posts/error-file-dir' which has no attached static file."));
    }

    @Test
    public void testErrorFilePageDirNotFound() {
        RestAssured.when().get("/posts/error-image-not-found").then().statusCode(500).log().ifValidationFails()
                .body(containsString(
                        "File 'not-found.png' not found in 'posts/error-image-not-found' directory (found: hello.png)."));
    }

    @Test
    public void testErrorImageSite() {
        RestAssured.when().get("/error-image-site").then().statusCode(500).log().ifValidationFails().body(containsString(
                "RoqStaticFileException: File 'images/not-found.png' not found in public dir"));
    }

    @Test
    public void testErrorStaticFileSite() {
        RestAssured.when().get("/error-static-file").then().statusCode(500).log().ifValidationFails()
                .body(containsString("RoqStaticFileException: File 'not-found.pdf' not found in public dir"));
    }

}
