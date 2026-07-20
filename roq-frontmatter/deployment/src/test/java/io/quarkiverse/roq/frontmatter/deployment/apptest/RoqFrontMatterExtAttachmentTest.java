package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

@DisplayName("Roq FrontMatter - Collection directory post with :ext! link and attachments")
public class RoqFrontMatterExtAttachmentTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "ext-site")
            .withApplicationRoot((jar) -> jar.addAsResource("ext-site"));

    @Test
    @DisplayName("Directory collection post served at flat .html URL via :ext! template link")
    public void testExtPostServed() {
        RestAssured.when().get("/posts/2024-12-06-series-post.html").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Series Post"));
    }

    @Test
    @DisplayName("Sibling attachment served at dir path despite :ext! link producing .html URL")
    public void testExtPostAttachmentServed() {
        RestAssured.when().get("/posts/2024-12-06-series-post/series.foo.webp").then().statusCode(200).log()
                .ifValidationFails();
    }
}
