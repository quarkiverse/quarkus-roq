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
        // Check experience section
        assertThat(body).contains("I transform boring markdown files into beautiful websites");
        assertThat(body).contains("Aug 2024 to Present");
        assertThat(body).contains("Static Site Generator");

        // Check education section
        assertThat(body).contains("School of Quarkus");
        assertThat(body).contains("Making Java Cool Again for Web Development");

        // Check skills section
        assertThat(body).contains("Super Powers");
        assertThat(body).contains("Party Tricks");

        // Check profile
        assertThat(body).contains("Iam");
        assertThat(body).contains("Roq");
        assertThat(body).contains(
                "Roq is a modern static site generator built on Quarkus, combining the power of Java with the simplicity of static site generation.");
    }

    @Test
    public void testStyle() {
        final String body = RestAssured.when().get(bundle.style("app")).then().statusCode(200).log().ifValidationFails()
                .extract()
                .asString();
        assertThat(body).contains("--color-rose-50:oklch(96.9% .015 12.422)");
        assertThat(body).contains("--color-cyan-200:oklch(91.7% .08 205.041);");
        assertThat(body).contains(".items-center");
        assertThat(body).contains(".max-w-7xl");
    }
}
