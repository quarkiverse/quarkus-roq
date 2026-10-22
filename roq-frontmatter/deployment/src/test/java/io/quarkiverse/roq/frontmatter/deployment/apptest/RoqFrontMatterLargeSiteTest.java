package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code target/large-site} (local, generated)
 * <p>
 * Features tested: a site with many documents and pages can be built. Every recorded page used to
 * add constants to a single generated recorder class, overflowing the JVM constant pool
 * ({@code ClassTooLargeException}) above ~4,700 documents.
 */
@DisplayName("Roq FrontMatter - Large site (constant pool limit)")
public class RoqFrontMatterLargeSiteTest {

    static final int POSTS = 12_000;
    static final int PAGES = 3_000;
    static final String SITE_DIR = "target/large-site";

    static {
        generateSite(Path.of(SITE_DIR));
    }

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.dir", SITE_DIR);

    @Test
    @DisplayName("Index lists the collection size and the page count")
    public void testIndex() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'posts-count' }.text()", equalTo(String.valueOf(POSTS)))
                .body("html.body.article.span.find { it.@class == 'pages-count' }.text()", equalTo(String.valueOf(PAGES + 1)));
    }

    @Test
    @DisplayName("First and last posts are routed and rendered")
    public void testPosts() {
        RestAssured.when().get("/posts/post-0").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.h1", equalTo("Post 0"))
                .body("html.body.article.span", equalTo("value-0"));
        RestAssured.when().get("/posts/post-" + (POSTS - 1)).then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.h1", equalTo("Post " + (POSTS - 1)));
    }

    @Test
    @DisplayName("First and last pages are routed and rendered")
    public void testPages() {
        RestAssured.when().get("/page-0").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.h1", equalTo("Page 0"));
        RestAssured.when().get("/page-" + (PAGES - 1)).then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.h1", equalTo("Page " + (PAGES - 1)));
    }

    private static void generateSite(Path site) {
        try {
            if (Files.exists(site.resolve("content/posts/post-" + (POSTS - 1) + ".html"))
                    && Files.exists(site.resolve("content/pages/page-" + (PAGES - 1) + ".html"))) {
                return;
            }
            Files.createDirectories(site.resolve("templates/layouts"));
            Files.createDirectories(site.resolve("content/posts"));
            Files.createDirectories(site.resolve("content/pages"));
            Files.writeString(site.resolve("templates/layouts/default.html"), """
                    <!DOCTYPE html>
                    <html>
                    <head><title>{page.title}</title></head>
                    <body>
                    <article>
                    <h1>{page.title}</h1>
                    {#insert /}
                    </article>
                    </body>
                    </html>
                    """);
            Files.writeString(site.resolve("content/index.html"), """
                    ---
                    title: Large site
                    layout: default
                    ---
                    <span class="posts-count">{site.collections.posts.size}</span>
                    <span class="pages-count">{site.pages.size}</span>
                    """);
            for (int i = 0; i < POSTS; i++) {
                Files.writeString(site.resolve("content/posts/post-" + i + ".html"), """
                        ---
                        layout: default
                        title: Post %1$d
                        slug: post-%1$d
                        date: 2024-01-01
                        foo: value-%1$d
                        ---
                        <span>{page.data.foo}</span>
                        """.formatted(i));
            }
            for (int i = 0; i < PAGES; i++) {
                Files.writeString(site.resolve("content/pages/page-" + i + ".html"), """
                        ---
                        layout: default
                        title: Page %1$d
                        link: /page-%1$d
                        ---
                        <p>Page body</p>
                        """.formatted(i));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
