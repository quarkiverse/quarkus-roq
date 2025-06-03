package io.quarkiverse.roq;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class RoqTest {

    @Test
    public void testErrorFilePage() {
        RestAssured.when().get("/posts/error-file").then().statusCode(500).log().ifValidationFails().body(containsString(
                "RoqStaticFileException: Can't find file 'not-found.pdf' attached to the page"));
    }

    @Test
    public void testEscapeDataPage() {
        RestAssured.when().get("/posts/escape-data").then().statusCode(200).log().ifValidationFails().body(containsString(
                "{foo} {{{{}}}}} bar {#if}"));
    }

    @Test
    public void testEscapedConfigPage() {
        RestAssured.when().get("/posts/escaped-config").then().statusCode(200).log().ifValidationFails().body(containsString(
                "{foo} {{{{}}}}} bar {#if}"));
    }

    @Test
    public void testErrorImagePage() {
        RestAssured.when().get("/posts/error-image").then().statusCode(500).log().ifValidationFails().body(containsString(
                "RoqStaticFileException: File 'images/not-found.png' not found in public dir"));
    }

    @Test
    public void testErrorImagePageDir() {
        RestAssured.when().get("/posts/error-image-dir").then().statusCode(500).log().ifValidationFails().body(containsString(
                "Can't find 'not-found.png' in  'posts/error-image-dir' which has no attached static file."));
    }

    @Test
    public void testErrorFilePageDir() {
        RestAssured.when().get("/posts/error-file-dir").then().statusCode(500).log().ifValidationFails().body(containsString(
                "Can't find 'not-found.pdf' in  'posts/error-file-dir' which has no attached static file."));
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

    @Test
    public void testMdPost() {
        RestAssured.when().get("/posts/k8s-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("K8S Post - Hello, world! I'm Roq"))
                .body(containsString("<h1 class=\"page-title\">K8S Post</h1>"))
                .body(containsString("<code class=\"language-yaml\">---"))
                .body(containsString("This is an attached file: /hello.txt"))
                .body(containsString("Legacy: /static/assets/images/legacy.png"))
                .body(containsString("ROQ overridden"));
    }

    @Test
    public void testPageDir() {
        RestAssured.when().get("/posts/2010-08-05-hello-world").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("posts/2010-08-05-hello-world/index.md - Hello, world! I'm Roq"))
                .body(containsString("<h1 class=\"page-title\">posts/2010-08-05-hello-world/index.md"))
                .body(containsString(
                        "Here are the links: /posts/2010-08-05-hello-world/hello.pdf and /posts/2010-08-05-hello-world/hello.pdf"))
                .body(containsString(
                        "and an images: /images/hello.png, /images/hello.foo.png and /posts/2010-08-05-hello-world/hello-page.png and  /posts/2010-08-05-hello-world/hello-page.png"))
                .body(containsString("page by path: /lo-you/"))
                .body(containsString("document by path: /posts/k8s-post/"));
        RestAssured.when().get("/images/hello.foo.png").then().statusCode(200).log().ifValidationFails();
    }

    @Test
    public void testCodestartPost() {
        RestAssured.when().get("/posts/the-first-roq").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("The First Roq! - Hello, world! I'm Roq"))
                .body(containsString("You can access page data like this"));
    }

    @Test
    public void testCodestartAbout() {
        RestAssured.when().get("/about").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("About - Hello, world! I'm Roq"))
                .body(containsString(
                        "Roq is a powerful static site generator that combines the best features of tools like Jekyll and Hugo"))
                .body(containsString("Roqqy Balboa"));
    }

    @Test
    public void testPublicFiles() {
        RestAssured.when().get("/hello.txt").then().statusCode(200).log().ifValidationFails()
                .body(equalTo("Hello {world}"));
    }

    @Test
    public void testCodestart404() {
        RestAssured.when().get("/404.html").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Oops! Roq is saying 404 - Hello, world! I'm Roq"));
    }

    @Test
    public void testCodestartIndex() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Hello, world! I'm Roq"))
                .body(containsString("Ready to Roq my world!"));
    }
}
