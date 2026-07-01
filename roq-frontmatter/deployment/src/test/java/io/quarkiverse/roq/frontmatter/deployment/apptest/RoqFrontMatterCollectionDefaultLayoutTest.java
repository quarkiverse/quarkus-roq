package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

@DisplayName("Roq FrontMatter - Default layout preserved when overriding collection config")
public class RoqFrontMatterCollectionDefaultLayoutTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.dir", "src/test/collection-default-layout-site")
            .overrideConfigKey("site.collections.posts.future", "true")
            .overrideConfigKey("site.collections.posts.hidden", "false")
            .overrideConfigKey("site.collections.posts.link", "/posts/:slug/");

    @Test
    @DisplayName("Default layout (post) is preserved when other properties are overridden")
    public void testDefaultLayoutPreservedOnOverride() {
        RestAssured.when().get("/2026/06/first-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.@class", equalTo("post-layout"));
    }
}
