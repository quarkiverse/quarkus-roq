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
    public void testLongUrlsCanWrap() {
        final String body = RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        // Item links display the raw URL: without wrap-anywhere, a long URL widens the whole page on phones
        assertThat(body).containsPattern(
                "<a\\s+class=\"[^\"]*\\bwrap-anywhere\\b[^\"]*\"\\s+href=\"https://archive\\.example\\.org/lovelace/");
        assertThat(body).containsPattern("class=\"[^\"]*\\bmarkdown wrap-anywhere\\b");
        final String css = RestAssured.when().get(bundle.style("app")).then().statusCode(200).log().ifValidationFails()
                .extract()
                .asString();
        assertThat(css).contains(".wrap-anywhere{overflow-wrap:anywhere}");
    }

    @Test
    public void testSectionAnchors() {
        final String body = RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        assertThat(body).containsPattern("<h3[^>]*id=\"Experience\"");
        assertThat(body).contains("href=\"#Experience\"");
        assertThat(body).contains("Copy link");
        // Items get an anchor too, prefixed by their section id to stay unique
        assertThat(body).contains("id=\"Education-1833-Meeting-Charles-Babbage\"");
        assertThat(body).contains("href=\"#Education-1833-Meeting-Charles-Babbage\"");
    }

    @Test
    public void testCollapsibleItems() {
        final String body = RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        // collapsed: true -> <details> without "open"
        assertThat(body).containsPattern(
                "<li id=\"Experience-1843-Notes-on-the-Analytical-Engine\"[^>]*>\\s*<details\\s*>");
        // collapsed: false -> <details open>
        assertThat(body).containsPattern(
                "<li id=\"Experience-1840-1842-Correspondence-with-Augustus-De-Morgan\"[^>]*>\\s*<details open>");
        // The anchor of a collapsible item sits in its <summary>, so it stays visible when collapsed
        assertThat(body).containsPattern(Pattern.compile(
                "<summary>(?:(?!</summary>).)*href=\"#Experience-1843-Notes-on-the-Analytical-Engine\"",
                Pattern.DOTALL));
    }

    @Test
    public void testNestedItemAnchors() {
        final String body = RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        // Sub-item ids are prefixed by their parent item id
        assertThat(body).contains(
                "id=\"Experience-1843-Notes-on-the-Analytical-Engine-Note-A-Beyond-calculation\"",
                "href=\"#Experience-1843-Notes-on-the-Analytical-Engine-Note-A-Beyond-calculation\"",
                "id=\"Experience-1843-Notes-on-the-Analytical-Engine-Note-G-Bernoulli-numbers-algorithm\"",
                "id=\"Experience-1840-1842-Correspondence-with-Augustus-De-Morgan-1841-Calculus-lessons\"");
        // ... and rendered inside the (collapsed) parent <details>
        assertThat(body).containsPattern(Pattern.compile(
                "<li id=\"Experience-1843-Notes-on-the-Analytical-Engine\"[^>]*>\\s*<details\\s*>"
                        + "(?:(?!</details>).)*<li id=\"Experience-1843-Notes-on-the-Analytical-Engine-Note-G-Bernoulli-numbers-algorithm\"",
                Pattern.DOTALL));
    }

    @Test
    public void testItemsBeyondThreeAreBehindShowMore() {
        final String body = RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        // The 4th item of a section is hidden until "Show more" is clicked, but keeps its anchor
        assertThat(body).containsPattern(
                "x-show=\"showAll \\|\\| 3 < 3\" x-cloak>\\s*<li id=\"Experience-1835-1852-Countess-of-Lovelace\"");
        assertThat(body).contains("href=\"#Experience-1835-1852-Countess-of-Lovelace\"");
        assertThat(body).contains("'... Show more'");
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
    public void test404UsesPageNotFoundBodyClass() {
        final String body = RestAssured.when().get("/404.html").then().statusCode(200).log().ifValidationFails()
                .extract()
                .asString();
        assertThat(body).containsPattern("<body[^>]*class=\"[^\"]*page-not-found[^\"]*\"");
    }

    @Test
    public void testDocumentShellIsNotDuplicated() {
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
