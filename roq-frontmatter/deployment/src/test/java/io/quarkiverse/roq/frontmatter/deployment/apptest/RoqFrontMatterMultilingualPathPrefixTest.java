package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code multilingual-inherit} (resource)
 * <p>
 * Config: site.default-locale=fr, site.path-prefix=/bar/
 * <p>
 * Features tested: with a path prefix, the translation URL is the prefix, then the language, then the default
 * variant's path, and the default variant URL keeps the prefix only.
 */
@DisplayName("Roq FrontMatter - Multilingual with path prefix")
public class RoqFrontMatterMultilingualPathPrefixTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "multilingual-inherit")
            .overrideConfigKey("site.default-locale", "fr")
            .overrideConfigKey("site.path-prefix", "/bar/")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("multilingual-inherit"));

    @Test
    @DisplayName("Default variant is served under the prefix")
    public void testDefaultVariantUnderPrefix() {
        RestAssured.when().get("/bar/posts/patrimoine/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("href=\"/bar/en/posts/patrimoine/\""));
    }

    @Test
    @DisplayName("Translation is served under the prefix and the language")
    public void testTranslationUnderPrefixAndLang() {
        RestAssured.when().get("/bar/en/posts/patrimoine/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Inheritance"));
        RestAssured.when().get("/en/posts/patrimoine/").then().statusCode(404);
    }
}
