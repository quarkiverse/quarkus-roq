package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * Site: {@code alt-syntax-site} (resource)
 * <p>
 * Config: {@code quarkus.qute.alt-expr-syntax=true}
 * <p>
 * Verifies that pages and layouts using {@code {=expr}} alternative
 * expression syntax render correctly.
 */
@DisplayName("Roq FrontMatter - Alt expression syntax")
public class RoqFrontMatterAltSyntaxTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "alt-syntax-site")
            .overrideConfigKey("quarkus.default-locale", "en")
            .overrideConfigKey("site.time-zone", "UTC")
            .overrideConfigKey("quarkus.qute.alt-expr-syntax", "true")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("alt-syntax-site"));

    @Test
    @DisplayName("Page renders with alt syntax expressions")
    public void testPage() {
        RestAssured.when().get("/page/alt-page").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Alt Syntax Page - Alt Syntax Site"))
                .body("html.body.article.h1", equalTo("Alt Syntax Page"))
                .body("html.body.article.p", equalTo("Alt syntax works"))
                .body("html.body.article.span.find { it.@class == 'literal' }.text()", equalTo("{not-evaluated}"));
    }

    @Test
    @DisplayName("Post renders with alt syntax expressions")
    public void testPost() {
        RestAssured.when().get("/the-posts/alt-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Alt Post - Alt Syntax Site"))
                .body("html.body.article.h1", equalTo("Alt post heading"))
                .body("html.body.article.p", equalTo("This post uses alt expression syntax."));
    }

    @Test
    @DisplayName("Index lists posts with alt syntax")
    public void testIndex() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Alt Syntax Site - Alt Syntax Site"))
                .body("html.body.div.h1", containsString("Alt Post"))
                .body("html.body.span.find { it.@class == 'site-title' }.text()", equalTo("Alt Syntax Site"));
    }
}
