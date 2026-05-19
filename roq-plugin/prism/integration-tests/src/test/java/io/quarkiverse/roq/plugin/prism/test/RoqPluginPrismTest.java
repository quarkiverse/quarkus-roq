package io.quarkiverse.roq.plugin.prism.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;

@QuarkusTest
public class RoqPluginPrismTest {

    @Test
    void prismJsBundleIsServedAndContainsRequestedAndTransitiveLanguages() {
        Response response = RestAssured.when().get("/static/bundle/prism.js")
                .then().statusCode(200).log().ifValidationFails().extract().response();
        String body = response.asString();
        assertThat(response.contentType()).contains("javascript");

        // Prism core (an IIFE) must be present; the global is exported as 'Prism'
        assertThat(body).contains("Prism=function");
        // Explicitly requested languages: bash and java (java is registered via extend("clike", ...))
        assertThat(body).contains("languages.bash=");
        assertThat(body).contains("languages.java=");
        // 'java' transitively requires 'clike'; the resolver must include it
        assertThat(body).contains("Prism.languages.clike=");
        // Auto-highlight trigger appended by the build step
        assertThat(body).contains("Prism.highlightAll()");
    }

    @Test
    void prismCssBundleIsServedAndMatchesConfiguredTheme() {
        Response response = RestAssured.when().get("/static/bundle/prism.css")
                .then().statusCode(200).log().ifValidationFails().extract().response();
        String body = response.asString();
        // The 'tomorrow' theme uses #2d2d2d as its code background; this byte sequence
        // is unique to the dark themes and absent from the default theme.
        assertThat(body).contains("#2d2d2d");
    }

    @Test
    void prismTagInjectsScriptAndStylesheetIntoRenderedPage() {
        String body = RestAssured.when().get("/")
                .then().statusCode(200).log().ifValidationFails().extract().asString();
        assertThat(body).contains("/static/bundle/prism.js");
        assertThat(body).contains("/static/bundle/prism.css");
        // The {#prism /} tag emits a <script> and a <link rel="stylesheet">
        assertThat(body).containsPattern("<script[^>]+src=\"[^\"]*/static/bundle/prism\\.js");
        assertThat(body).containsPattern("<link[^>]+href=\"[^\"]*/static/bundle/prism\\.css");
    }
}
