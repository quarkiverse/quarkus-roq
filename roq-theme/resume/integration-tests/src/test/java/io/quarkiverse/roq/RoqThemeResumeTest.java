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
        assertThat(body).contains("Mathematician and Writer");
        assertThat(body).contains("1842 - 1843");

        // Check education section
        assertThat(body).contains("Private Tutoring");
        assertThat(body).contains("Meeting Charles Babbage");

        // Check skills section
        assertThat(body).contains("Mathematics");
        assertThat(body).contains("Languages");

        // Check profile
        assertThat(body).contains("Ada");
        assertThat(body).contains("Lovelace");
        assertThat(body).contains("Computational Pioneer");
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
