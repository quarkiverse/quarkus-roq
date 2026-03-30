package io.quarkiverse.roq.frontmatter.deployment;

import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * Site: {@code collection-site} (mixed local + resource)
 * <p>
 * Config: quarkus.roq.dir=src/test/collection-site, quarkus.roq.resource-dir=collection-site-resources,
 * site.theme=my-theme, site.collections.guides=true, site.collections.guides.layout=guide
 * <p>
 * Features tested: multi-collection site (posts + guides), collection counts,
 * next/prev navigation, date-based link patterns, pagination (size, total, pages),
 * author filtering, past() extension, readTime, local file overriding resource file,
 * theme layout override by local layout, layout chain rendering.
 */
@DisplayName("Roq FrontMatter - Collections, navigation, pagination, theme override, and local/resource priority")
public class RoqFrontMatterCollectionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.dir", "src/test/collection-site")
            .overrideConfigKey("quarkus.roq.resource-dir", "collection-site-resources")
            .overrideConfigKey("site.theme", "my-theme")
            .overrideConfigKey("site.collections.posts", "true")
            .overrideConfigKey("site.collections.guides", "true")
            .overrideConfigKey("site.collections.guides.layout", "guide")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("collection-site-resources"));

    // --- Collections ---

    @Test
    @DisplayName("Posts collection has 3 documents")
    public void testPostsCollectionSize() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'posts-count' }.text()", equalTo("3"));
    }

    @Test
    @DisplayName("Guides collection has 2 documents")
    public void testGuidesCollectionSize() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'guides-count' }.text()", equalTo("2"));
    }

    @Test
    @DisplayName("Site has two collections")
    public void testCollectionListSize() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'collection-list-size' }.text()", equalTo("2"));
    }

    // --- Navigation (sorted newest-first: third -> second -> first) ---

    @Test
    @DisplayName("Newest post has next but no previous")
    public void testNewestPostNavigation() {
        RestAssured.when().get("/2024/03/third-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'has-next' }.text()", equalTo("true"))
                .body("html.body.article.span.find { it.@class == 'has-prev' }.text()", equalTo("false"));
    }

    @Test
    @DisplayName("Middle post has both next and previous")
    public void testMiddlePostNavigation() {
        RestAssured.when().get("/2024/02/second-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'has-next' }.text()", equalTo("true"))
                .body("html.body.article.span.find { it.@class == 'has-prev' }.text()", equalTo("true"));
    }

    @Test
    @DisplayName("Oldest post has previous but no next")
    public void testOldestPostNavigation() {
        RestAssured.when().get("/2024/01/first-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'has-next' }.text()", equalTo("false"))
                .body("html.body.article.span.find { it.@class == 'has-prev' }.text()", equalTo("true"));
    }

    @Test
    @DisplayName("Next from middle post is oldest post")
    public void testNextFromMiddle() {
        RestAssured.when().get("/2024/02/second-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'next-title' }.text()", equalTo("First Post"));
    }

    @Test
    @DisplayName("Previous from middle post is newest post")
    public void testPrevFromMiddle() {
        RestAssured.when().get("/2024/02/second-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'prev-title' }.text()", equalTo("Third Post"));
    }

    // --- Custom link patterns ---

    @Test
    @DisplayName("Post URL uses date-based pattern")
    public void testPostDateBasedUrl() {
        RestAssured.when().get("/2024/01/first-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'page-url' }.text()", containsString("/2024/01/"));
    }

    @Test
    @DisplayName("Guide URL uses slug-based pattern")
    public void testGuideSlugUrl() {
        RestAssured.when().get("/guides/advanced-usage").then().statusCode(200).log().ifValidationFails();
    }

    // --- Pagination ---

    @Test
    @DisplayName("First page has 2 paginated posts")
    public void testFirstPagePostCount() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.findAll { it.@class == 'post-title' }.size()", equalTo(2));
    }

    @Test
    @DisplayName("Paginator total is 2 pages")
    public void testPaginatorTotal() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'paginator-total' }.text()", equalTo("2"));
    }

    @Test
    @DisplayName("Paginator collection size is 3")
    public void testPaginatorCollectionSize() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'paginator-collection-size' }.text()", equalTo("3"));
    }

    @Test
    @DisplayName("Paginator first page is first")
    public void testPaginatorIsFirst() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'paginator-is-first' }.text()", equalTo("true"));
    }

    @Test
    @DisplayName("Second pagination page is accessible")
    public void testSecondPaginationPage() {
        // First get the next page URL from the paginator
        String nextPath = RestAssured.when().get("/").then().statusCode(200)
                .extract().htmlPath()
                .getString("html.body.span.find { it.@class == 'paginator-next' }.text()");
        RestAssured.when().get(nextPath).then().statusCode(200).log().ifValidationFails();
    }

    // --- Filtering & extensions ---

    @Test
    @DisplayName("Filter posts by author returns correct count")
    public void testFilterByAuthor() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'alice-posts-count' }.text()", equalTo("2"));
    }

    @Test
    @DisplayName("Past posts returns all posts")
    public void testPastPosts() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'past-posts-count' }.text()", equalTo("3"));
    }

    @Test
    @DisplayName("Read time is calculated for post")
    public void testReadTime() {
        RestAssured.when().get("/2024/01/first-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'read-time' }.text()",
                        greaterThanOrEqualTo("1"));
    }

    // --- Local/resource priority ---

    @Test
    @DisplayName("Local file overrides resource with same path")
    public void testLocalOverridesResource() {
        RestAssured.when().get("/guides/getting-started").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.h1", equalTo("Getting Started (Local Override)"));
    }

    @Test
    @DisplayName("Resource-only file is still available")
    public void testResourceOnlyFile() {
        RestAssured.when().get("/guides/advanced-usage").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'collection-id' }.text()", equalTo("guides"));
    }

    @Test
    @DisplayName("Guide collection ID is correct")
    public void testGuideCollectionId() {
        RestAssured.when().get("/guides/getting-started").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'collection-id' }.text()", equalTo("guides"));
    }

    @Test
    @DisplayName("Guide collection size includes both sources")
    public void testGuideCollectionSizeIncludesBothSources() {
        RestAssured.when().get("/guides/getting-started").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'collection-size' }.text()", equalTo("2"));
    }

    // --- Multi-origin directory page attachments ---

    @Test
    @DisplayName("Mixed-origin gallery is a directory page")
    public void testMixedGalleryIsIndex() {
        RestAssured.when().get("/mixed-gallery").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'is-index' }.text()", equalTo("true"));
    }

    @Test
    @DisplayName("Mixed-origin gallery has attached files")
    public void testMixedGalleryHasFiles() {
        RestAssured.when().get("/mixed-gallery").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'has-files' }.text()", equalTo("true"));
    }

    @Test
    @DisplayName("Mixed-origin gallery has 2 attachments from both origins")
    public void testMixedGalleryFileCount() {
        RestAssured.when().get("/mixed-gallery").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'files-count' }.text()", equalTo("2"));
    }

    @Test
    @DisplayName("Local attachment exists in mixed-origin gallery")
    public void testMixedGalleryLocalFileExists() {
        RestAssured.when().get("/mixed-gallery").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'file-exists-local' }.text()", equalTo("true"));
    }

    @Test
    @DisplayName("Resource attachment exists in mixed-origin gallery")
    public void testMixedGalleryResourceFileExists() {
        RestAssured.when().get("/mixed-gallery").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'file-exists-resource' }.text()", equalTo("true"));
    }

    @Test
    @DisplayName("Local attachment is served from mixed-origin gallery")
    public void testMixedGalleryLocalFileServed() {
        RestAssured.when().get("/mixed-gallery/local-photo.png").then().statusCode(200).log().ifValidationFails();
    }

    @Test
    @DisplayName("Resource attachment is served from mixed-origin gallery")
    public void testMixedGalleryResourceFileServed() {
        RestAssured.when().get("/mixed-gallery/resource-photo.png").then().statusCode(200).log().ifValidationFails();
    }

    // --- Theme layout override ---

    @Test
    @DisplayName("Local layout overrides theme layout")
    public void testLocalLayoutOverridesTheme() {
        RestAssured.when().get("/2024/01/first-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'is-override' }.text()", equalTo("true"));
    }

    @Test
    @DisplayName("Theme layout class is not present when overridden")
    public void testThemeClassNotPresent() {
        String body = RestAssured.when().get("/2024/01/first-post").then().statusCode(200)
                .extract().body().asString();
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("post-override"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("themed-post"));
    }

    // --- Layout chain ---

    @Test
    @DisplayName("Post renders through layout chain with override class")
    public void testPostLayoutChain() {
        String body = RestAssured.when().get("/2024/01/first-post").then().statusCode(200)
                .extract().body().asString();
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("<article class=\"post-override\">"));
    }

    @Test
    @DisplayName("Guide renders through layout chain with guide class")
    public void testGuideLayoutChain() {
        String body = RestAssured.when().get("/guides/getting-started").then().statusCode(200)
                .extract().body().asString();
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("<article class=\"guide\">"));
    }
}
