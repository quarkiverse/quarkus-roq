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
        assertThat(body).contains("https://x.com/quarkusio");
        assertThat(body).contains("https://github.com/quarkiverse/quarkus-roq");
        assertThat(body).contains("https://www.linkedin.com/in/quarkusio");
    }

    @Test
    public void testAbout() {
        final String body = RestAssured.when().get("/about").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        assertThat(body).contains("<title>About - My Roq Site</title>");
        assertThat(body).contains("About this site");
        assertThat(body).contains("Roqqy Balboa");
    }

    @Test
    public void testPost() {
        final String body = RestAssured.when().get("/posts/the-first-roq").then().statusCode(200).log()
                .ifValidationFails().extract()
                .asString();
        assertThat(body).contains("<title>The First Roq! - My Roq Site</title>");
        assertThat(body).contains("You can access page data like this");
        assertThat(body).contains("page-share");
        assertThat(body).contains("Share this article");
        assertThat(body).contains(
                "https://x.com/intent/post?text=The+First+Roq%21&amp;url=https%3A%2F%2Fmywebsite.com%2Fposts%2Fthe-first-roq%2F");
        assertThat(body).contains(
                "https://www.facebook.com/sharer/sharer.php?u=https%3A%2F%2Fmywebsite.com%2Fposts%2Fthe-first-roq%2F");
        assertThat(body).contains(
                "https://bsky.app/intent/compose?text=The+First+Roq%21%0Ahttps%3A%2F%2Fmywebsite.com%2Fposts%2Fthe-first-roq%2F");
        assertThat(body).contains(
                "https://www.linkedin.com/sharing/share-offsite/?url=https%3A%2F%2Fmywebsite.com%2Fposts%2Fthe-first-roq%2F");
    }

    @Test
    public void testPostWithDefaultAvatar() {
        final String body = RestAssured.when().get("/posts/post-without-avatar").then().statusCode(200).log()
                .ifValidationFails().extract()
                .asString();
        assertThat(body).contains("<title>Post Without Avatar");
        assertThat(body).contains("default-avatar.svg");
    }

    @Test
    public void test404() {
        final String body = RestAssured.when().get("/404.html").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        assertThat(body).contains("<title>Oops! Roq is saying 404 - My Roq Site</title>");
    }

    @Test
    public void test404BodyClassMatchesWrapperDivClass() {
        final String body = RestAssured.when().get("/404.html").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        assertThat(body).containsPattern("<body[^>]*class=\"[^\"]*page-not-found[^\"]*\"");
        assertThat(body).containsPattern("<div class=\"wrapper[^\"]*page-not-found[^\"]*\"");
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
