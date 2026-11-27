package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code multilingual-inherit} (resource)
 * <p>
 * Config: site.default-locale=fr
 * <p>
 * Features tested: a translation without its own layout inherits the layout of its default variant (it is not a
 * collection member, so it would otherwise get the page layout); a default variant may declare {@code lang} equal
 * to the default locale; a non-language suffix ({@code index.v2.adoc}) is an ordinary file, not a translation.
 */
@DisplayName("Roq FrontMatter - Multilingual inheritance and explicit lang")
public class RoqFrontMatterMultilingualInheritTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "multilingual-inherit")
            .overrideConfigKey("site.default-locale", "fr")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("multilingual-inherit"));

    @Test
    @DisplayName("Translation inherits the post layout of its default variant")
    public void testTranslationInheritsLayout() {
        RestAssured.when().get("/en/posts/patrimoine/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("<article class=\"post\">"))
                .body(not(containsString("<article class=\"page\">")));
    }

    @Test
    @DisplayName("Translation without a date inherits its default variant's date")
    public void testTranslationInheritsDate() {
        String defaultDate = RestAssured.when().get("/posts/patrimoine/").then().statusCode(200)
                .log().ifValidationFails()
                .extract().htmlPath().getString("html.body.article.span.find { it.@class == 'page-date' }.text()");
        String translationDate = RestAssured.when().get("/en/posts/patrimoine/").then().statusCode(200)
                .log().ifValidationFails()
                .extract().htmlPath().getString("html.body.article.span.find { it.@class == 'page-date' }.text()");
        assertThat(defaultDate).isNotBlank();
        assertThat(translationDate).isEqualTo(defaultDate);
    }

    @Test
    @DisplayName("Default variant keeps its own layout and links to the inherited translation")
    public void testDefaultVariantLayout() {
        RestAssured.when().get("/posts/patrimoine/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("<article class=\"post\">"))
                .body(containsString("href=\"/en/posts/patrimoine/\""));
    }

    @Test
    @DisplayName("Default variant may declare lang equal to the default locale")
    public void testDefaultVariantExplicitLang() {
        RestAssured.when().get("/posts/langue-explicite/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("data-lang=\"fr\""))
                .body(containsString("href=\"/en/posts/langue-explicite/\""));
        RestAssured.when().get("/en/posts/langue-explicite/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Explicit text"))
                .body(containsString("data-lang=\"en\""));
    }

    @Test
    @DisplayName("index.md is the default variant of index.en.md")
    public void testMarkdownIndexTranslation() {
        RestAssured.when().get("/posts/article-qute/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Texte qute fr."))
                .body(containsString("data-lang=\"fr\" data-translation=\"false\""))
                .body(containsString("href=\"/en/posts/article-qute/\""));
        RestAssured.when().get("/en/posts/article-qute/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Qute text en."))
                .body(containsString("data-lang=\"en\" data-translation=\"true\""))
                .body(containsString("href=\"/posts/article-qute/\""));
    }

    @Test
    @DisplayName("index.v2.adoc is an ordinary file when the feature is on, not a translation")
    public void testNonLanguageSuffixIsOrdinary() {
        RestAssured.when().get("/posts/version-deux/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("<nav class=\"translations\"></nav>"));
        RestAssured.when().get("/en/posts/version-deux/").then().statusCode(404);
    }
}
