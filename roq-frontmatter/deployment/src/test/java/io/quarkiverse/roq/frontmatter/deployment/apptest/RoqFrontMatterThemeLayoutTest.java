package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.allOf;
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
 * Features tested: theme layout resolution — legacy syntax, simple names,
 * theme-layout key, cross-theme references, extension stripping, user overrides.
 */
@DisplayName("Roq FrontMatter - Theme layout resolution")
public class RoqFrontMatterThemeLayoutTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.dir", "src/test/theme-layout-site")
            .overrideConfigKey("site.theme", "my-theme");

    // --- Legacy: :theme/ syntax ---

    @Test
    @DisplayName(":theme/ syntax resolves to theme layout")
    public void testThemeColonSyntax() {
        RestAssured.when().get("/pages/about").then().statusCode(200).log().ifValidationFails()
                .body(allOf(
                        containsString("themed-about"),
                        containsString("About page content")));
    }

    // --- Legacy: full theme-layouts/ path ---

    @Test
    @DisplayName("Full theme-layouts/ path resolves to theme layout")
    public void testFullThemeLayoutsPath() {
        RestAssured.when().get("/pages/old-full-path").then().statusCode(200).log().ifValidationFails()
                .body(allOf(
                        containsString("themed-page"),
                        containsString("Old full path page content")));
    }

    // --- User override at layouts/{theme-name}/ ---

    @Test
    @DisplayName("Layout override at layouts/{theme-name}/ overrides theme layout")
    public void testOldPathLayoutOverride() {
        RestAssured.when().get("/pages/override-test").then().statusCode(200).log().ifValidationFails()
                .body(allOf(
                        containsString("override-custom"),
                        containsString("Override test page content")));
    }

    // --- layout: foo (local-first + theme fallback) ---

    @Test
    @DisplayName("Simple layout name resolves to theme layout as fallback")
    public void testSimpleNameThemeFallback() {
        RestAssured.when().get("/pages/simple-name").then().statusCode(200).log().ifValidationFails()
                .body(allOf(
                        containsString("themed-about"),
                        containsString("Simple name page content")));
    }

    @Test
    @DisplayName("Local layout takes precedence over theme layout")
    public void testLocalLayoutPrecedence() {
        RestAssured.when().get("/pages/local-priority").then().statusCode(200).log().ifValidationFails()
                .body(allOf(
                        containsString("local-page"),
                        containsString("Local priority page content")));
    }

    // --- theme-layout: foo (direct theme reference) ---

    @Test
    @DisplayName("theme-layout explicitly targets theme layout")
    public void testThemeLayoutExplicit() {
        RestAssured.when().get("/pages/explicit-theme").then().statusCode(200).log().ifValidationFails()
                .body(allOf(
                        containsString("themed-page"),
                        containsString("Explicit theme page content")));
    }

    @Test
    @DisplayName("theme-layout: with fully qualified theme/name")
    public void testThemeLayoutFullyQualified() {
        RestAssured.when().get("/pages/explicit-theme-qualified").then().statusCode(200).log().ifValidationFails()
                .body(allOf(
                        containsString("themed-page"),
                        containsString("Fully qualified theme-layout reference")));
    }

    // --- Cross-theme reference ---

    @Test
    @DisplayName("layout: other-theme/bar resolves cross-theme")
    public void testCrossThemeReference() {
        RestAssured.when().get("/pages/cross-theme").then().statusCode(200).log().ifValidationFails()
                .body(allOf(
                        containsString("other-theme-bar"),
                        containsString("Cross-theme layout reference")));
    }

    // --- Extension stripping ---

    @Test
    @DisplayName("layout: about.html strips extension and resolves")
    public void testExtensionStripping() {
        RestAssured.when().get("/pages/extension-test").then().statusCode(200).log().ifValidationFails()
                .body(allOf(
                        containsString("themed-about"),
                        containsString("Layout reference with .html extension")));
    }
}
