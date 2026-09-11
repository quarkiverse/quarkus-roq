package io.quarkiverse.roq;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;

@QuarkusTest
public class RoqThemeLinktreeTest {

    private static final RestAssuredConfig TIMEOUT_CONFIG = RestAssuredConfig.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                    .setParam("http.socket.timeout", (int) TimeUnit.SECONDS.toMillis(60))
                    .setParam("http.connection.timeout", (int) TimeUnit.SECONDS.toMillis(60)));

    @Test
    void testLinktreeLayout() {
        String body = given().config(TIMEOUT_CONFIG)
                .when().get("/")
                .then()
                .statusCode(200)
                .extract().asString();
        assertThat(body).doesNotContain("{|").doesNotContain("|}");
        assertThat(body).doesNotContain("{#");
        assertThat(body).contains("Ada Lovelace");
        assertThat(body).contains("@adalovelace");
        assertThat(body).contains("Computational Pioneer");
        assertThat(body).contains("ada.png");
        assertThat(body).contains("ph-fill ph-github-logo");
        assertThat(body).contains("https://example.com");
        assertThat(body).contains("Test Link");
        assertThat(body).contains("A test link");
        assertThat(body).contains("ph-tree-structure");
    }

    @Test
    void testHtmlLangAttribute() {
        String body = given().config(TIMEOUT_CONFIG)
                .when().get("/")
                .then()
                .statusCode(200)
                .extract().asString();
        // No 'lang' frontmatter is set in this sample site, so it falls back to the JVM default
        // locale; just verify the attribute is rendered with a non-empty value (not hardcoded "en").
        assertThat(body).containsPattern("<html lang=\"[^\"]+\"");
    }

    @Test
    void testLinktreesLayout() {
        String body = given().config(TIMEOUT_CONFIG)
                .when().get("/trees")
                .then()
                .statusCode(200)
                .extract().asString();
        assertThat(body).doesNotContain("{|").doesNotContain("|}");
        assertThat(body).doesNotContain("{#");
        assertThat(body).contains("All Trees");
        assertThat(body).contains("Ada Lovelace");
        assertThat(body).contains("Test Links");
        assertThat(body).contains("Test Link");
        assertThat(body).contains("downloadQR");
    }

    @Test
    void testThemeBackgroundClassesOnHtmlAndBody() {
        String body = given().config(TIMEOUT_CONFIG)
                .when().get("/")
                .then()
                .statusCode(200)
                .extract().asString();
        // The overscroll-edge background must stay on <html> itself, not merely appear
        // somewhere in the document (e.g. only on an inner wrapper div).
        assertThat(body).containsPattern("<html[^>]*class=\"[^\"]*bg-slate-100[^\"]*dark:bg-slate-900[^\"]*\"");
        assertThat(body).containsPattern(
                "<body[^>]*class=\"[^\"]*bg-slate-100[^\"]*dark:bg-slate-900[^\"]*overscroll-none[^\"]*min-h-screen[^\"]*\"");
    }

    @Test
    void testHtmlClassFrontmatterOverridesDefault() {
        // `html-class` frontmatter (as recognized by roq-base/default's <html> tag) must be
        // honored once roq-linktree extends roq-base/default.
        String body = given().config(TIMEOUT_CONFIG)
                .when().get("/html-class-test")
                .then()
                .statusCode(200)
                .extract().asString();
        assertThat(body).containsPattern("<html[^>]*class=\"[^\"]*qa-html-class[^\"]*\"");
    }

    @Test
    void testDocumentShellIsNotDuplicated() {
        String body = given().config(TIMEOUT_CONFIG)
                .when().get("/")
                .then()
                .statusCode(200)
                .extract().asString();
        assertThat(countOccurrences(body, "<!DOCTYPE html>")).isEqualTo(1);
        assertThat(countOccurrences(body, "<html ")).isEqualTo(1);
        assertThat(countOccurrences(body, "<head>")).isEqualTo(1);
        assertThat(countOccurrences(body, "<body")).isEqualTo(1);
    }

    private static long countOccurrences(String text, String literal) {
        return Pattern.compile(Pattern.quote(literal)).matcher(text).results().count();
    }

    @Test
    void testTreeCollectionPage() {
        String body = given().config(TIMEOUT_CONFIG)
                .when().get("/trees/test-links/")
                .then()
                .statusCode(200)
                .extract().asString();
        assertThat(body).doesNotContain("{|").doesNotContain("|}");
        assertThat(body).doesNotContain("{#");
        assertThat(body).contains("Ada Lovelace");
        assertThat(body).contains("Test Links");
        assertThat(body).contains("Test Link");
        assertThat(body).contains("https://example.com");
    }
}
