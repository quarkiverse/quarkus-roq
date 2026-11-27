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
 * Site: {@code multilingual-site} (resource)
 * <p>
 * Config: site.default-locale=fr
 * <p>
 * Features tested: an article folder with a default variant ({@code index.adoc}) and a translation
 * ({@code index.en.adoc}) is grouped by the scanner. The default variant is served at the default URL
 * and is the only collection member; the folder resources are attached once and shared by every variant.
 * The translation is served under {@code /<lang>} followed by the default variant's URL path (its slug comes
 * from the default variant, not from its own title). Each variant exposes {@code page.lang} and
 * {@code page.translations}. The test layout prints {@code data-lang} and a {@code nav.translations} list.
 * An index file with a token that is not a catalogue language ({@code about/index.v2.adoc}) is an ordinary file.
 */
@DisplayName("Roq FrontMatter - Multilingual articles")
public class RoqFrontMatterMultilingualTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "multilingual-site")
            .overrideConfigKey("site.default-locale", "fr")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("multilingual-site"));

    @Test
    @DisplayName("Default variant is served at the default URL")
    public void testDefaultVariant() {
        RestAssured.when().get("/posts/mon-article/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Mon article"));
    }

    @Test
    @DisplayName("Folder resource is served under the default URL")
    public void testDefaultVariantResource() {
        RestAssured.when().get("/posts/mon-article/diagram.svg").then().statusCode(200).log().ifValidationFails();
    }

    @Test
    @DisplayName("Only the default variants are listed in the collection")
    public void testTranslationNotInCollection() {
        // two articles (mon-article, sans-traduction); the translation of mon-article is not listed
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.'**'.find { it.@class == 'posts-count' }.text()", equalTo("2"));
    }

    @Test
    @DisplayName("Translation shares the folder attachments")
    public void testTranslationSharesAttachments() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.'**'.find { it.@class == 'translation-files' }.text()", equalTo("1"));
    }

    @Test
    @DisplayName("Default variant has the default lang and links to its translation")
    public void testDefaultVariantLangAndTranslations() {
        RestAssured.when().get("/posts/mon-article/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("data-lang=\"fr\""))
                .body(containsString("href=\"/en/posts/mon-article/\">🇬🇧 en</a>"));
    }

    @Test
    @DisplayName("Translation is served under the language prefix with the default variant's slug")
    public void testTranslationUrl() {
        RestAssured.when().get("/en/posts/mon-article/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("My article"))
                .body(containsString("data-lang=\"en\""))
                .body(containsString("href=\"/posts/mon-article/\""));
    }

    @Test
    @DisplayName("Translation does not take its slug from its own title")
    public void testTranslationNotAtOwnTitleSlug() {
        RestAssured.when().get("/en/posts/my-article/").then().statusCode(404);
    }

    @Test
    @DisplayName("Folder resource is also served under the translation URL")
    public void testTranslationResource() {
        RestAssured.when().get("/en/posts/mon-article/diagram.svg").then().statusCode(200).log().ifValidationFails();
    }

    @Test
    @DisplayName("llms.txt lists the default variant and its translation")
    public void testLlmsTxtListsTranslation() {
        RestAssured.when().get("/llms.txt").then().statusCode(200).log().ifValidationFails()
                .body(containsString("[Mon article](/posts/mon-article/)"))
                .body(containsString("[My article](/en/posts/mon-article/)"));
    }

    @Test
    @DisplayName("Article without translation keeps its default URL and has no translation link")
    public void testArticleWithoutTranslation() {
        RestAssured.when().get("/posts/sans-traduction/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("data-lang=\"fr\""))
                .body(containsString("<nav class=\"translations\"></nav>"))
                .body(not(containsString("/en/posts/sans-traduction/")));
        RestAssured.when().get("/en/posts/sans-traduction/").then().statusCode(404);
    }

    @Test
    @DisplayName("index.<token>.<ext> with a token outside the language catalogue is an ordinary file")
    public void testUnknownTokenIsOrdinaryFile() {
        RestAssured.when().get("/about/index-v2/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Version two"));
        RestAssured.when().get("/v2/about/").then().statusCode(404);
    }
}
