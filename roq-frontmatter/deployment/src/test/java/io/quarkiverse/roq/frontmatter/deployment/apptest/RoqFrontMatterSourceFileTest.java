package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * Site: {@code basic-site} (resource)
 * <p>
 * Config: defaults
 * <p>
 * Features tested: SourceFile metadata (absolutePath, relativePath), page ID,
 * page URL, file names (base and source), source path, site-level counts
 * (pages, collections, allPages). This is the TDD anchor for the absolutePath fix.
 */
@DisplayName("Roq FrontMatter - Source file metadata and path resolution")
public class RoqFrontMatterSourceFileTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "basic-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("basic-site"));

    @Test
    @DisplayName("Source path is relative to content directory")
    public void testSourcePath() {
        RestAssured.when().get("/page/some-page").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'source-path' }.text()",
                        equalTo("pages/some-page.html"));
    }

    @Test
    @DisplayName("Absolute path resolves to existing file on disk")
    public void testAbsolutePath() {
        RestAssured.when().get("/page/some-page").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'source-file-absolute' }.text()",
                        endsWith("content/pages/some-page.html"));
    }

    @Test
    @DisplayName("Relative path includes content prefix")
    public void testRelativePath() {
        RestAssured.when().get("/page/some-page").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'source-file-relative' }.text()",
                        containsString("content/pages/some-page.html"));
    }

    @Test
    @DisplayName("Page ID matches source path")
    public void testPageId() {
        RestAssured.when().get("/page/some-page").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'page-id' }.text()",
                        equalTo("pages/some-page.html"));
    }

    @Test
    @DisplayName("Page URL matches configured link")
    public void testPageUrl() {
        RestAssured.when().get("/page/some-page").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'page-url' }.text()",
                        equalTo("/page/some-page/"));
    }

    @Test
    @DisplayName("Base file name strips extension")
    public void testBaseFileName() {
        RestAssured.when().get("/page/some-page").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'base-file-name' }.text()",
                        equalTo("some-page"));
    }

    @Test
    @DisplayName("Source file name includes extension")
    public void testSourceFileName() {
        RestAssured.when().get("/page/some-page").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'source-file-name' }.text()",
                        equalTo("some-page.html"));
    }

    @Test
    @DisplayName("Post source path includes collection directory")
    public void testPostSourcePath() {
        RestAssured.when().get("/the-posts/new-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'source-path' }.text()",
                        startsWith("posts/"));
    }

    @Test
    @DisplayName("Post collection ID is posts")
    public void testPostCollectionId() {
        RestAssured.when().get("/the-posts/new-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'collection-id' }.text()",
                        equalTo("posts"));
    }

    @Test
    @DisplayName("Post absolute path resolves to existing file")
    public void testPostAbsolutePath() {
        RestAssured.when().get("/the-posts/new-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'source-file-absolute' }.text()",
                        containsString("content/posts/"));
    }

    @Test
    @DisplayName("Site title from frontmatter data")
    public void testSiteTitle() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'site-title' }.text()",
                        equalTo("Simple Site"));
    }

    @Test
    @DisplayName("Site page count")
    public void testSitePageCount() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'page-count' }.text().toInteger()",
                        greaterThanOrEqualTo(2));
    }

    @Test
    @DisplayName("Site collection count")
    public void testSiteCollectionCount() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'collection-count' }.text()",
                        equalTo("1"));
    }

    @Test
    @DisplayName("All pages count includes documents")
    public void testAllPagesCount() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'all-pages-count' }.text().toInteger()",
                        greaterThanOrEqualTo(5));
    }
}
