package io.quarkiverse.roq;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTestProfile;
import io.restassured.RestAssured;

public abstract class AbstractRoqTest {

    @Test
    public void testHelloNoExt() {
        RestAssured.given().when().get("/hello").then().statusCode(200).log().ifValidationFails()
                .body(equalTo("Hello"));
    }

    @Test
    public void testEmpty() {
        RestAssured.given().when().get("/empty.txt").then().statusCode(200).log().ifValidationFails()
                .body(emptyString());
    }

    @Test
    public void testSpecialChars() {
        RestAssured.given().when().get("/élo you$@/").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("élo you$@.html - Hello, world! I'm Roq"))
                .body(containsString("This is a test layout:"))
                .body(containsString("href=\"/%C3%A9lo%20you$@/\""))
                .body(containsString("It should work fine"));
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
                .body(containsString("page by path: /%C3%A9lo%20you$@/"))
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

    public static class RoqAndRollProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "roq-and-roll";
        }
    }
}
