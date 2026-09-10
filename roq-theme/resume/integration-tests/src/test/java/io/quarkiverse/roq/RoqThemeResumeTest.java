package io.quarkiverse.roq;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;

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
    public void testHtmlLangAttribute() {
        final String body = RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        // No 'lang' frontmatter is set in this sample site, so it falls back to the JVM default
        // locale; just verify the attribute is rendered with a non-empty value (not hardcoded "en").
        assertThat(body).containsPattern("<html lang=\"[^\"]+\"");
    }

    @Test
    public void testBodyClassFrontmatterAppliedToBodyTag() {
        // The `body-class` frontmatter key (as recognized by roq-base/default's <body> tag)
        // must be honored once roq-resume extends roq-base/default.
        final String body = RestAssured.when().get("/body-class-test").then().statusCode(200).log()
                .ifValidationFails().extract()
                .asString();
        assertThat(body).containsPattern("<body[^>]*class=\"[^\"]*qa-body-class[^\"]*\"");
    }

    @Test
    public void test404UsesPageNotFoundBodyClass() {
        // The 404.html layout's `body-class: page-not-found` frontmatter key must still reach
        // the <body> tag now that it flows through roq-base/default's body-class mechanism.
        final String body = RestAssured.when().get("/404.html").then().statusCode(200).log().ifValidationFails()
                .extract()
                .asString();
        assertThat(body).containsPattern("<body[^>]*class=\"[^\"]*page-not-found[^\"]*\"");
    }

    @Test
    public void testDocumentShellIsNotDuplicated() {
        // roq-resume/default.html now extends roq-base/default instead of redefining the full
        // document shell; guard against the shell (or its head-meta override) being emitted twice.
        final String body = RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        assertThat(countOccurrences(body, "<!DOCTYPE html>")).isEqualTo(1);
        assertThat(countOccurrences(body, "<html ")).isEqualTo(1);
        assertThat(countOccurrences(body, "<head>")).isEqualTo(1);
        assertThat(countOccurrences(body, "<body")).isEqualTo(1);
    }

    private static long countOccurrences(String text, String literal) {
        return Pattern.compile(Pattern.quote(literal)).matcher(text).results().count();
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
