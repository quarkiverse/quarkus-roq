package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * Site: {@code theme-layout-site} (local)
 * <p>
 * Config: quarkus.roq.dir=src/test/theme-layout-site, site.theme=my-theme
 * <p>
 * Features tested: theme layout resolution — legacy-theme :theme/ syntax (case a),
 * legacy-theme full theme-layouts/ path (case b), new simple layout name, theme-layout key.
 */
@DisplayName("Roq FrontMatter - Theme layout resolution")
public class RoqFrontMatterThemeLayoutTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.dir", "src/test/theme-layout-site")
            .overrideConfigKey("site.theme", "my-theme");

    // --- Case (a): :theme/ syntax → resolves to theme layout ---

    @Test
    @DisplayName(":theme/ syntax resolves to theme layout")
    public void testThemeColonSyntax() {
        RestAssured.when().get("/pages/about").then().statusCode(200).log().ifValidationFails()
                .body(containsString("themed-about"));
    }

    // --- Case (b): full theme-layouts/ path → resolves to theme layout ---

    @Test
    @DisplayName("Full theme-layouts/ path resolves to theme layout")
    public void testFullThemeLayoutsPath() {
        RestAssured.when().get("/pages/old-full-path").then().statusCode(200).log().ifValidationFails()
                .body(containsString("themed-page"));
    }

    // --- Case (c): layout override at layouts/{theme-name}/ takes precedence ---

    @Test
    @DisplayName("Layout override at layouts/{theme-name}/ overrides theme layout")
    public void testOldPathLayoutOverride() {
        RestAssured.when().get("/pages/override-test").then().statusCode(200).log().ifValidationFails()
                .body(containsString("override-custom"));
    }

    // --- New syntax: layout: foo (local-first + theme fallback) ---

    @Test
    @DisplayName("Simple layout name resolves to theme layout as fallback")
    public void testSimpleNameThemeFallback() {
        RestAssured.when().get("/pages/simple-name").then().statusCode(200).log().ifValidationFails()
                .body(containsString("themed-about"));
    }

    @Test
    @DisplayName("Local layout takes precedence over theme layout")
    public void testLocalLayoutPrecedence() {
        RestAssured.when().get("/pages/local-priority").then().statusCode(200).log().ifValidationFails()
                .body(containsString("local-page"));
    }

    // --- New syntax: theme-layout: foo ---

    @Test
    @DisplayName("theme-layout explicitly targets theme layout")
    public void testThemeLayoutExplicit() {
        RestAssured.when().get("/pages/explicit-theme").then().statusCode(200).log().ifValidationFails()
                .body(containsString("themed-page"));
    }
}
