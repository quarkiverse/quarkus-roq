package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code multilingual-reserved-keys} (resource). Config: site.default-locale=fr.
 * <p>
 * Features tested: a {@code translations} key set explicitly in front matter does not fail the build and the page is
 * served like any other page. A lone {@code translation-of} is covered by RoqFrontMatterTranslationGroupTest.
 */
@DisplayName("Roq FrontMatter - Multilingual keys set explicitly in front matter")
public class RoqFrontMatterReservedLanguageKeysTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "multilingual-reserved-keys")
            .overrideConfigKey("site.default-locale", "fr")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("multilingual-reserved-keys"));

    @Test
    @DisplayName("Page with a user translations key is served")
    public void testUserTranslationsKeyIgnored() {
        RestAssured.when().get("/posts/a/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("A"));
    }
}
