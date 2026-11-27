package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code page-lang-site} (resource). Config: site.default-locale=en.
 * <p>
 * Features tested: {@code page.lang} uses the same fallback chain as {@code <html lang>}: page lang, then the site
 * lang (the index page sets {@code lang: fr}), then {@code site.default-locale}.
 */
@DisplayName("Roq FrontMatter - page.lang falls back to the site lang before site.default-locale")
public class RoqFrontMatterPageLangSiteFallbackTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "page-lang-site")
            .overrideConfigKey("site.default-locale", "en")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("page-lang-site"));

    @Test
    @DisplayName("A page without lang takes the site lang")
    public void testPageLangFallsBackToSiteLang() {
        RestAssured.when().get("/about").then().statusCode(200).log().ifValidationFails()
                .body(containsString("data-lang=\"en\""));
    }
}
