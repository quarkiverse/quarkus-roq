package io.quarkiverse.roq;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class RoqThemeTheCodeTest {

    @Test
    void testHomePage() {
        String body = given()
                .when().get("/")
                .then()
                .statusCode(200)
                .extract().body().asString();
        assertThat(body).contains("Hi, I'm Your Name!");
        assertThat(body).contains("Just a coder");
    }

    @Test
    void testAboutPage() {
        String body = given()
                .when().get("/about")
                .then()
                .statusCode(200)
                .extract().body().asString();
        assertThat(body).contains("About");
    }

    @Test
    void testBlogPost() {
        String body = given()
                .when().get("/posts/welcome-to-your-blog")
                .then()
                .statusCode(200)
                .extract().body().asString();
        assertThat(body).contains("Welcome to your blog!");
    }

    @Test
    void test404Page() {
        String body = given()
                .when().get("/404")
                .then()
                .statusCode(200)
                .extract().body().asString();
        assertThat(body).contains("404");
    }
}
