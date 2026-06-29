package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code routing-site} (resource)
 * <p>
 * Config: custom page-link and collection link templates
 * <p>
 * Features tested: global link template configuration via
 * {@code site.page-link} and {@code site.collections.<name>.link}.
 */
@DisplayName("Roq FrontMatter - Global link template configuration")
public class RoqFrontMatterLinkConfigTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "routing-site")
            .overrideConfigKey("site.page-link", "/:slug/")
            .overrideConfigKey("site.collections.posts.link", "/:collection/:name/")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("routing-site"));

    @Test
    @DisplayName("Post uses collection link config (/:collection/:name/) instead of default")
    public void testPostUsesCollectionLinkConfig() {
        // awesome-post.html has slug: awesome-post-1, but :name = awesome-post (filename)
        RestAssured.when().get("/posts/awesome-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("My Cool Post"));
    }

    @Test
    @DisplayName("Post is NOT served at default slug-based URL")
    public void testPostNotAtDefaultUrl() {
        // With /:collection/:name/, the slug-based URL should not exist
        RestAssured.when().get("/posts/awesome-post-1").then().statusCode(404);
    }

    @Test
    @DisplayName("Page without explicit link uses site page-link config (/:slug/)")
    public void testPageUsesPageLinkConfig() {
        // about.html has title "About Us", no explicit link → uses site.page-link = /:slug/
        RestAssured.when().get("/about-us").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("About Us"));
    }

    @Test
    @DisplayName("Page without explicit link is NOT served at default path-based URL")
    public void testPageNotAtDefaultUrl() {
        RestAssured.when().get("/pages/about").then().statusCode(404);
    }

    @Test
    @DisplayName("Page with explicit frontmatter link ignores site page-link config")
    public void testExplicitLinkOverridesConfig() {
        // cool-page.html has explicit link: /my-cool-page — config doesn't affect it
        RestAssured.when().get("/my-cool-page").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("My Cool Page"));
    }
}
