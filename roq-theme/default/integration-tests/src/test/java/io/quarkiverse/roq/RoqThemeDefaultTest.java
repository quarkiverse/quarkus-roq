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
    public void testResumeContent() {
        final String body = RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        assertThat(body).containsIgnoringWhitespaces(
                """
                        <p>Writing with Roq is delightful, it is all free and Open Source ❤️.</p>
                        """);
        assertThat(body).contains("Ready to Roq my world!");
        assertThat(body).contains("Let's Roq the basics");
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
