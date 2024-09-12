package io.quarkiverse.roq.frontmatter.deployment;

import static org.hamcrest.Matchers.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.frontmatter.runtime.Page;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class RoqFrontMatterTest {

    // Start unit test with your extension loaded
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClass(MyResource.class)
                    .addAsResource("application.properties")
                    .addAsResource("site/_data/site.yml")
                    .addAsResource("site/_includes/foo/bar.html")
                    .addAsResource("site/_includes/view.html")
                    .addAsResource("site/_includes/header.html")
                    .addAsResource("site/_layouts/default.html")
                    .addAsResource("site/_layouts/page.html")
                    .addAsResource("site/_layouts/post.html")
                    .addAsResource("site/index.html")
                    .addAsResource("site/_posts/awesome-post.html")
                    .addAsResource("site/_posts/markdown-post.md")
                    .addAsResource("site/pages/cool-page.html"));

    @Test
    public void testHtmlPost() {
        RestAssured.when().get("/posts/awesome-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("My Cool Post"))
                .body("html.head.base.@href", equalTo("/foo"))
                .body("html.head.meta.findAll { it.@name == 'twitter:url' }.@content", equalTo("https://mywebsite.com/foo"))
                .body("html.body.article.h1", equalTo("A cool blog post"))
                .body("html.body.article.p", equalTo("bar"))
                .body("html.body.div.h1", equalTo("My Cool Post"))
                .body("html.body.div.p", equalTo("bar bar bar"));
    }

    @Test
    public void testMdPost() {
        RestAssured.when().get("/posts/markdown-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Markdown Post"))
                .body("html.body.article.h1", equalTo("A post made with markdown"))
                .body("html.body.article.blockquote.p", equalTo("bar"))
                .body("html.body.div.h1", equalTo("Markdown Post"))
                .body("html.body.div.p", equalTo("bar bar bar"))
        // language=html
        ;
    }

    @Test
    public void testPage() {
        RestAssured.when().get("/my-cool-page").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("My Cool Page"))
                .body("html.body.article.h1", equalTo("Hello World"))
                .body("html.body.article.p", equalTo("bar"))
                .body("html.body.div.h1", equalTo("My Cool Page"))
                .body("html.body.div.p", equalTo("bar bar bar"))
        // language=html
        ;
    }

    @Test
    public void testIndex() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("My Website"))
                .body("html.body.div[0]", containsString("posts/awesome-post"))
                .body("html.body.div[0]", containsString("posts/markdown-post"))
                .body("html.body.div.h1", equalTo("My Website"))
                .body("html.body.div.p", equalTo("bar bar bar"));
    }

    @ApplicationScoped
    public static class MyResource {

        @Inject
        @Named("posts/awesome-post")
        Page awesomePostFm;

        @Inject
        @Named("posts/markdown-post")
        Page markdownPostFm;

        @Inject
        @Named("pages/cool-page")
        Page coolPageFm;

    }
}
