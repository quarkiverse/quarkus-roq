package io.quarkiverse.roq.data.test;

import static org.hamcrest.Matchers.equalToCompressingWhiteSpace;

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
                // language=html
                .body(equalToCompressingWhiteSpace("""
                        <!DOCTYPE html>
                        <html>
                        <head>
                          <title>My Cool Post</title>
                          <meta name="description" content="this is a very awesome post"/>
                        </head>
                        <body>
                        <article class="post">
                          <h1>A cool blog post</h1>
                          <p>bar</p>
                        </article>
                        <div class="view">
                          <h1>My Cool Post</h1>
                          <p>bar bar bar</p>
                        </div>
                        </body>
                        </html>
                        """));
    }

    @Test
    public void testMdPost() {
        RestAssured.when().get("/posts/markdown-post").then().statusCode(200).log().ifValidationFails()
                // language=html
                .body(equalToCompressingWhiteSpace("""
                        <!DOCTYPE html>
                        <html>
                        <head>
                          <title>Markdown Post</title>
                          <meta name="description" content="this is a post made with markdown"/>
                        </head>
                        <body>
                        <article class="post">
                          <h1>A post made with markdown</h1>
                          <blockquote>
                            <p>bar</p>
                          </blockquote>
                        </article>
                        <div class="view">
                          <h1>Markdown Post</h1>
                          <p>bar bar bar</p>
                        </div>
                        </body>
                        </html>
                        """));
    }

    @Test
    public void testPage() {
        RestAssured.when().get("/my-cool-page").then().statusCode(200).log().ifValidationFails()
                // language=html
                .body(equalToCompressingWhiteSpace("""

                         <!DOCTYPE html>
                        <html>
                        <head>
                          <title>My Cool Page</title>
                          <meta name="description" content="this is a very cool page"/>
                        </head>
                        <body>
                        <article class="page">
                          <h1>Hello World</h1>
                          <p>bar</p>
                        </article>
                        <div class="view">
                          <h1>My Cool Page</h1>
                          <p>bar bar bar</p>
                        </div>
                        </body>
                        </html>
                        \s"""));
    }

    @Test
    public void testIndex() {
        RestAssured.when().get("/").then().statusCode(200).log().everything();
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
