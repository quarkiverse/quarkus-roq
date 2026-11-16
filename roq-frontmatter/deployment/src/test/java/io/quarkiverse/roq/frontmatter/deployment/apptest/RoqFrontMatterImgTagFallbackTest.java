package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code files-site} (resource)
 * <p>
 * Features tested: without the image plugin, the {@code {#img}} tag used by themes renders a plain {@code <img>}
 * with the source and the other parameters as attributes, and nothing when the source is null.
 */
@DisplayName("Roq FrontMatter - img tag fallback without the image plugin")
public class RoqFrontMatterImgTagFallbackTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "files-site")
            .withApplicationRoot((jar) -> jar.addAsResource("files-site"));

    @Test
    @DisplayName("The page image renders as a plain img with the other parameters as attributes")
    void testPageImageRendersPlainImg() {
        String body = RestAssured.when().get("/posts/ext-post.html").then().statusCode(200).log().ifValidationFails()
                .extract().asString();
        assertThat(body, containsString(
                "<div class=\"hero\"><img src=\"/posts/ext-post/chart.svg\" alt=\"Chart\" class=\"page-image\" loading=\"eager\"></div>"));
    }

    @Test
    @DisplayName("A null source renders nothing")
    void testNullSourceRendersNothing() {
        String body = RestAssured.when().get("/posts/ext-post.html").then().statusCode(200).log().ifValidationFails()
                .extract().asString();
        assertThat(body, containsString("<div class=\"no-image\"></div>"));
    }
}
