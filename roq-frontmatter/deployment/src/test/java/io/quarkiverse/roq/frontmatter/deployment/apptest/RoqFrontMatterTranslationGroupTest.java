package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code multilingual-groups} (resource). Config: site.default-locale=fr.
 * <p>
 * Features tested: a translation group can be created from front matter. Pages sharing a {@code translation-of}
 * value form one group, across files ({@code posts/alpha.adoc} and {@code posts/alpha-en.adoc}). A page with
 * {@code translation-of} joins a file-based group ({@code posts/2026/other.adoc} joins {@code posts/2026/mon-article}).
 * A lone {@code translation-of} forms no group. Translations are served under {@code /<lang>} with the default
 * variant's slug, and are not collection members.
 */
@DisplayName("Roq FrontMatter - Translation groups created from front matter")
public class RoqFrontMatterTranslationGroupTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "multilingual-groups")
            .overrideConfigKey("site.default-locale", "fr")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("multilingual-groups"));

    @Test
    @DisplayName("Only default-language pages are collection members")
    public void testTranslationsNotInCollection() {
        // alpha, mon-article and solo; alpha-en, index.en and other are translations
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.'**'.find { it.@class == 'posts-count' }.text()", equalTo("3"));
    }

    @Test
    @DisplayName("Default page lists its front matter translation")
    public void testDefaultListsFrontMatterTranslation() {
        RestAssured.when().get("/posts/alpha/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("href=\"/en/posts/alpha/\""));
    }

    @Test
    @DisplayName("Front matter translation is served under its language with the default slug")
    public void testFrontMatterTranslationServed() {
        RestAssured.when().get("/en/posts/alpha/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Alpha EN"))
                .body(containsString("lang=\"en\""))
                .body(containsString("href=\"/posts/alpha/\""));
    }

    @Test
    @DisplayName("Front matter page joins a file-based group")
    public void testMixedGroup() {
        RestAssured.when().get("/posts/mon-article/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("href=\"/en/posts/mon-article/\""))
                .body(containsString("href=\"/de/posts/mon-article/\""));
        RestAssured.when().get("/de/posts/mon-article/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Other DE"));
    }

    @Test
    @DisplayName("A lone translation-of forms no group")
    public void testLoneTranslationOfIsNotAGroup() {
        RestAssured.when().get("/posts/solo/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Solo"))
                .body(not(containsString("class=\"translations\"><a")));
    }
}
