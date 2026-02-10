package io.quarkiverse.roq;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class RoqThemeResumeTest {

    @Inject
    Bundle bundle;

    @Test
    public void testResumeContent() {
        final String body = RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        assertThat(body).containsIgnoringWhitespaces(
                """
                        <p>A bunch of Quarkus contributors started this new initiative to allow Static Site Generation with Quarkus (similar to Hugo, Jekyll, Lume, ...).
                                                              Quarkus already provides most of the pieces to create great web applications ([<a href="https://quarkus.io/guides/web">https://quarkus.io/guides/web</a>][quarkus-web-docs]).
                                                              And Roq adds the missing pieces!</p>
                        """);
        assertThat(body).contains("August 2024 to Present");
        assertThat(body).contains("Static Site Generator");
        assertThat(body).contains("Iam");
        assertThat(body).contains("Roq");
        assertThat(body).contains(
                "A static site generator (SSG) that makes it fun and easy to build websites and blogs. It’s built with Java and Quarkus under the hood—but you don’t need to know them.");
        assertThat(body).contains("Foo-Foo");
        assertThat(body).contains("<p>Lorem ipsum dolor sit amet, consectetur");
        assertThat(body).contains("Bar-Bar");
        assertThat(body).contains("<p>Ut velit mauris, egestas sed");
        assertThat(body).contains("Baz-Baz");
        assertThat(body).contains("<p>Aliquam convallis sollicitudin purus.");
    }

    @Test
    public void testStyle() {
        final String body = RestAssured.when().get(bundle.style("app")).then().statusCode(200).log().ifValidationFails()
                .extract()
                .asString();
        assertThat(body).contains(".border-gray-100");
        assertThat(body).contains(".flex");
        assertThat(body).contains(".items-center");
        assertThat(body).contains(".max-w-7xl");
    }
}
