package io.quarkiverse.roq;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class RoqTest {

    @Test
    public void testMdPost() {
        RestAssured.when().get("/posts/k8s-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("K8S Post - Hello, world! I'm Roq"))
                .body(containsString("<h1 class=\"page-title\">K8S Post</h1>"))
                .body(containsString("<code class=\"language-yaml\">---"));
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
                .body(containsString("Roq stands out in the Java development community"))
                .body(containsString("Roqqy Balboa"));
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
