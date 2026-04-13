package io.quarkiverse.roq;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class RoqThemeDefaultTest {

    @Inject
    Bundle bundle;

    @Test
    public void testIndex() {
        final String body = RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        assertThat(body).contains("Roq</span> my world!");
        assertThat(body).contains("Roq the basics");
    }

    @Test
    public void testAbout() {
        final String body = RestAssured.when().get("/about").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        assertThat(body).contains("<title>About - Hello, world! I&#39;m Roq</title>");
        assertThat(body).contains("About this site");
        assertThat(body).contains("Roqqy Balboa");
    }

    @Test
    public void testPost() {
        final String body = RestAssured.when().get("/posts/the-first-roq").then().statusCode(200).log()
                .ifValidationFails().extract()
                .asString();
        assertThat(body).contains("<title>The First Roq! - Hello, world! I&#39;m Roq</title>");
        assertThat(body).contains("You can access page data like this");
    }

    @Test
    public void test404() {
        final String body = RestAssured.when().get("/404.html").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        assertThat(body).contains("<title>Oops! Roq is saying 404 - Hello, world! I&#39;m Roq</title>");
    }

    @Test
    public void testStyle() {
        final String body = RestAssured.when().get(bundle.style("app")).then().statusCode(200).log().ifValidationFails()
                .extract()
                .asString();
        // Check that @apply directives are compiling Tailwind utilities
        assertThat(body).contains("display:flex");
        assertThat(body).contains("align-items:center");
        assertThat(body).contains("max-width:80rem");
        // Check that component classes exist
        assertThat(body).contains(".sidebar");
    }
}
