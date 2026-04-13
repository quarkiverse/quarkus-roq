package io.quarkiverse.roq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class AntoroqThemeTest {

    @Test
    public void testAntoroqContent() {
        final String body = RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        assertThat(body).contains("Welcome to Antoroq Theme");
        assertThat(body).contains("Antoroq Documentation Theme");
    }

    @Test
    public void testAntoroqCSS() {
        final String css = RestAssured.when().get("/_static/antoroq.css").then().statusCode(200).log().ifValidationFails()
                .extract().asString();
        assertThat(css).contains("--color-brand-primary");
        assertThat(css).contains("--header-height");
        assertThat(css).contains("--nav-width");
    }

    @Test
    public void testHighlightJS() {
        RestAssured.when().get("/_static/vendor/highlight.js/highlight.min.js").then().statusCode(200);
    }

    @Test
    public void testAlpineJS() {
        final String body = RestAssured.when().get("/").then().statusCode(200).extract().asString();
        assertThat(body).contains("alpinejs");
    }

    @Test
    public void testNavigationStructure() {
        final String body = RestAssured.when().get("/").then().statusCode(200).extract().asString();
        assertThat(body).contains("nav");
        assertThat(body).contains("header");
        assertThat(body).contains("article");
        assertThat(body).contains("footer");
    }

    @Test
    public void test404Page() {
        RestAssured.when().get("/nonexistent-page").then().statusCode(404);
    }
}
