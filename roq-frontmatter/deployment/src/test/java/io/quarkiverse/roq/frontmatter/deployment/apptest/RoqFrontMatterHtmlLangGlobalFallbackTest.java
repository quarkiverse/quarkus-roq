package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.equalTo;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code html-lang-global-site} (resource)
 * <p>
 * Neither the page nor the site frontmatter sets {@code lang}, so the {@code <html lang="...">}
 * attribute must fall back to the JVM default locale ({@code global:locale}, i.e.
 * {@code Locale.getDefault()} — not affected by the {@code quarkus.default-locale} runtime config).
 * <p>
 * The page-lang and site-lang-fallback cases are covered by {@link RoqFrontMatterBaseThemeTest}
 * instead (its {@code base-theme-site} fixture sets a {@code lang} on the site and on one page).
 * This case needs a dedicated site with no {@code lang} set anywhere, since {@code site.data}
 * always comes from the index page's own frontmatter.
 */
@DisplayName("Roq FrontMatter - html lang attribute falls back to JVM default locale")
public class RoqFrontMatterHtmlLangGlobalFallbackTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "html-lang-global-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("html-lang-global-site"));

    @Test
    @DisplayName("Index page falls back to the JVM default locale")
    public void testGlobalLangFallback() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.@lang", equalTo(Locale.getDefault().toString()));
    }
}
