package io.quarkiverse.roq.data.test;

import static org.hamcrest.Matchers.equalToCompressingWhiteSpace;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterTest {

    // Start unit test with your extension loaded
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClass(MyResource.class)
                    .addAsResource("site/layout/header.html")
                    .addAsResource("site/layout/default.html")
                    .addAsResource("site/layout/page.html")
                    .addAsResource("site/layout/post.html")
                    .addAsResource("site/posts/awesome-post.html")
                    .addAsResource("site/pages/cool-page.html"));

    @Test
    public void writeYourOwnUnitTest() {
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
                        </body>
                        </html>
                        """));

        RestAssured.when().get("/cool-page").then().statusCode(200).log().ifValidationFails()
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
                        </body>
                        </html>
                        """));
    }

    @ApplicationScoped
    @Path("/")
    public static class MyResource {

        @Inject
        @Named("posts/awesome-post.html")
        JsonObject awesomePostFm;

        @Inject
        @Named("pages/cool-page.html")
        JsonObject coolPageFm;

        @CheckedTemplate(basePath = "pages", defaultName = CheckedTemplate.HYPHENATED_ELEMENT_NAME)
        public static class Pages {

            public static native TemplateInstance coolPage(Map<String, Object> fm);
        }

        @CheckedTemplate(basePath = "posts", defaultName = CheckedTemplate.HYPHENATED_ELEMENT_NAME)
        public static class Posts {

            public static native TemplateInstance awesomePost(Map<String, Object> fm);
        }

        @GET
        @Path("/posts/awesome-post")
        @Produces(MediaType.TEXT_HTML)
        public TemplateInstance awesomePost() {
            return Posts.awesomePost(awesomePostFm.getMap());
        }

        @GET
        @Path("/cool-page")
        @Produces(MediaType.TEXT_HTML)
        public TemplateInstance coolPageFm() {
            return Pages.coolPage(coolPageFm.getMap());
        }
    }
}
