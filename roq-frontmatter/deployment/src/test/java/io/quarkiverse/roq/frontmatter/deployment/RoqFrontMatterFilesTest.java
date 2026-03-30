package io.quarkiverse.roq.frontmatter.deployment;

import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * Site: {@code files-site} (resource)
 * <p>
 * Config: defaults
 * <p>
 * Features tested: directory vs single-file pages, file attachments (count,
 * existence check, serving), page/site image resolution (attached files,
 * public directory, legacy static/assets/images fallback), public file
 * serving, and fileExists checks.
 */
@DisplayName("Roq FrontMatter - Files, images, and attachments")
public class RoqFrontMatterFilesTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "files-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("files-site"));

    // --- Directory pages ---

    @Test
    @DisplayName("Single-file page is not an index")
    public void testSingleFilePageNotIndex() {
        RestAssured.when().get("/pages/simple-page").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'is-index' }.text()", equalTo("false"));
    }

    @Test
    @DisplayName("Single-file page has no attached files")
    public void testSingleFilePageNoFiles() {
        RestAssured.when().get("/pages/simple-page").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'has-files' }.text()", equalTo("false"));
    }

    @Test
    @DisplayName("Directory page is an index")
    public void testDirPageIsIndex() {
        RestAssured.when().get("/pages/gallery").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'is-index' }.text()", equalTo("true"));
    }

    @Test
    @DisplayName("Directory page has attached files")
    public void testDirPageHasFiles() {
        RestAssured.when().get("/pages/gallery").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'has-files' }.text()", equalTo("true"));
    }

    @Test
    @DisplayName("Attached file count includes deep sub-dir files")
    public void testAttachedFileCount() {
        RestAssured.when().get("/pages/gallery").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'files-count' }.text()", equalTo("3"));
    }

    @Test
    @DisplayName("Existing attached file is found")
    public void testFileExistsTrue() {
        RestAssured.when().get("/pages/gallery").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'file-exists-photo' }.text()", equalTo("true"));
    }

    @Test
    @DisplayName("Missing file is not found")
    public void testFileExistsFalse() {
        RestAssured.when().get("/pages/gallery").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'file-exists-missing' }.text()", equalTo("false"));
    }

    @Test
    @DisplayName("Deep sub-dir file without own index belongs to parent gallery")
    public void testDeepSubDirFileAttachedToParent() {
        RestAssured.when().get("/pages/gallery").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'file-exists-deep' }.text()", equalTo("true"));
    }

    @Test
    @DisplayName("Deep sub-dir file is served under parent path")
    public void testDeepSubDirFileServed() {
        RestAssured.when().get("/pages/gallery/images/deep.png").then().statusCode(200).log().ifValidationFails();
    }

    @Test
    @DisplayName("Nested index page with custom URL has its own attached files")
    public void testNestedIndexHasOwnFiles() {
        RestAssured.when().get("/pages/my-sub-gallery").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'has-files' }.text()", equalTo("true"))
                .body("html.body.article.span.find { it.@class == 'files-count' }.text()", equalTo("2"))
                .body("html.body.article.span.find { it.@class == 'file-exists-sub-image' }.text()",
                        equalTo("true"))
                .body("html.body.article.span.find { it.@class == 'file-exists-deep-sub' }.text()",
                        equalTo("true"));
    }

    @Test
    @DisplayName("Nested index attached file is served at custom URL path")
    public void testNestedIndexFileServed() {
        RestAssured.when().get("/pages/my-sub-gallery/sub-image.png").then().statusCode(200).log().ifValidationFails();
    }

    @Test
    @DisplayName("Deeply nested attachment under sub-index is served at custom URL path")
    public void testDeeplyNestedSubFileServed() {
        RestAssured.when().get("/pages/my-sub-gallery/nested/deep-sub.png").then().statusCode(200).log()
                .ifValidationFails();
    }

    @Test
    @DisplayName("Attached file is served")
    public void testAttachedFileServed() {
        RestAssured.when().get("/pages/gallery/photo1.png").then().statusCode(200).log().ifValidationFails();
    }

    @Test
    @DisplayName("Directory post serves attached SVG file")
    public void testDirPostAttachedFileServed() {
        RestAssured.when().get("/posts/dir-post/diagram.svg").then().statusCode(200).log().ifValidationFails();
    }

    // --- Images ---

    @Test
    @DisplayName("Directory page image resolves from attached files")
    public void testDirPageImageFromAttached() {
        RestAssured.when().get("/pages/gallery").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'page-image-url' }.text()",
                        containsString("photo1.png"));
    }

    @Test
    @DisplayName("Non-directory page image falls back to public images")
    public void testPageImageFallbackToPublic() {
        RestAssured.when().get("/pages/simple-page").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'page-image-url' }.text()",
                        containsString("images/logo.png"));
    }

    @Test
    @DisplayName("Site image resolves from public images directory")
    public void testSiteImage() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'site-image-url' }.text()",
                        containsString("images/cover.png"));
    }

    @Test
    @DisplayName("Site image exists check works")
    public void testSiteImageExists() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'site-image-exists' }.text()", equalTo("true"));
    }

    // --- Public files ---

    @Test
    @DisplayName("Site file exists from public directory")
    public void testSiteFileExists() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'site-file-exists' }.text()", equalTo("true"));
    }

    @Test
    @DisplayName("Public image is served")
    public void testPublicImageServed() {
        RestAssured.when().get("/images/cover.png").then().statusCode(200).log().ifValidationFails();
    }

    @Test
    @DisplayName("Public file is served")
    public void testPublicFileServed() {
        RestAssured.when().get("/docs/readme.txt").then().statusCode(200).log().ifValidationFails();
    }

    // --- Legacy static dir fallback ---

    @Test
    @DisplayName("Legacy image path is found via static/assets/images fallback")
    public void testLegacyImageFallback() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'legacy-image-exists' }.text()", equalTo("true"));
    }
}
