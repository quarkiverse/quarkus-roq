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
        assertThat(body).contains("Ready to");
        assertThat(body).contains("Roq");
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
                .when().get("/posts/the-first-roq")
                .then()
                .statusCode(200)
                .extract().body().asString();
        assertThat(body).contains("The First Roq!");
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
