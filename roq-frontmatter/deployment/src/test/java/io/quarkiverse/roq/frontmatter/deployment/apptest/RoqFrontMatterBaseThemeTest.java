package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code base-theme-site} (resource)
 * <p>
 * Verifies that roq-base theme layouts work as fallback when no local layouts are provided.
 * Also covers the {@code <html lang="...">} attribute: the site sets {@code lang: fr} and the
 * "about" page overrides it with {@code lang: de}, exercising the page → site fallback chain
 * (the JVM-default fallback case needs its own lang-free site, see
 * {@link RoqFrontMatterHtmlLangGlobalFallbackTest}).
 */
@DisplayName("Roq FrontMatter - Base theme layouts")
public class RoqFrontMatterBaseThemeTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "base-theme-site")
            .overrideConfigKey("quarkus.default-locale", "en")
            .overrideConfigKey("site.time-zone", "UTC")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("base-theme-site"));

    @Test
    @DisplayName("Page renders with roq-base page layout")
    public void testPage() {
        RestAssured.when().get("/about").then().statusCode(200).log().ifValidationFails()
                .body(containsString("<h1 class=\"page-title\">About</h1>"))
                .body(containsString("About page using roq-base page layout"));
    }

    @Test
    @DisplayName("Post renders with roq-base post layout")
    public void testPost() {
        RestAssured.when().get("/posts/hello-post").then().statusCode(200).log().ifValidationFails()
                .body(containsString("<h1 class=\"page-title\">Hello Post</h1>"))
                .body(containsString("A post using roq-base post layout."));
    }

    @Test
    @DisplayName("html lang attribute uses the page's FM lang when set")
    public void testHtmlLangFromPage() {
        RestAssured.when().get("/about").then().statusCode(200).log().ifValidationFails()
                .body("html.@lang", equalTo("de"));
    }

    @Test
    @DisplayName("html lang attribute falls back to the site's FM lang when the page has none")
    public void testHtmlLangFallsBackToSite() {
        RestAssured.when().get("/posts/hello-post").then().statusCode(200).log().ifValidationFails()
                .body("html.@lang", equalTo("fr"));
    }
}