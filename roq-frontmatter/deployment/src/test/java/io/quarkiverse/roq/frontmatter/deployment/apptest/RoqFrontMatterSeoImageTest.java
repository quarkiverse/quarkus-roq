package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code files-site} (resource)
 * <p>
 * Config: {@code quarkus.http.root-path=/blog}, {@code site.url=https://example.com}
 * <p>
 * Features tested: the SEO image tag goes through the {@code img} namespace; without the image plugin the
 * fallback resolver answers {@code img:url} with the page image as a {@link RoqUrl}, so the absolute URL carries
 * the site url and the root path exactly once.
 */
@DisplayName("Roq FrontMatter - SEO image through the img namespace")
public class RoqFrontMatterSeoImageTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "files-site")
            .overrideConfigKey("quarkus.http.root-path", "/blog")
            .overrideConfigKey("site.url", "https://example.com")
            .withApplicationRoot((jar) -> jar.addAsResource("files-site"));

    @Test
    @DisplayName("og:image is absolute with the root path once")
    void testOgImageAbsoluteWithRootPath() {
        String body = RestAssured.when().get("/posts/ext-post.html").then().statusCode(200).log().ifValidationFails()
                .extract().asString();
        assertThat(body, containsString(
                "property=\"og:image\" content=\"https://example.com/blog/posts/ext-post/chart.svg\""));
        assertThat(body, containsString(
                "name=\"twitter:image:src\" content=\"https://example.com/blog/posts/ext-post/chart.svg\""));
        assertThat(body, not(containsString("/blog/blog/")));
    }

    @Test
    @DisplayName("Pages without an image get a summary card")
    void testNoImageSummaryCard() {
        String body = RestAssured.when().get("/posts/file-post").then().statusCode(200).log().ifValidationFails()
                .extract().asString();
        assertThat(body, containsString("name=\"twitter:card\" content=\"summary\""));
        assertThat(body, not(containsString("og:image")));
    }
}
