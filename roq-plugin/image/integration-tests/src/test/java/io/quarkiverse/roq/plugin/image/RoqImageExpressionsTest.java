package io.quarkiverse.roq.plugin.image;

import static io.quarkiverse.roq.plugin.image.Pages.generatedPaths;
import static io.quarkiverse.roq.plugin.image.Pages.get;
import static io.quarkiverse.roq.plugin.image.Pages.section;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

/**
 * Image usages whose source is an expression (page.image, post.image, elvis, encoded attachment names) are
 * evaluated at build time against the site model and converted; remote, vector and missing images behave as
 * documented.
 */
@QuarkusTest
public class RoqImageExpressionsTest {

    @Test
    public void testPageExpressionsRender() {
        String body = get("/expressions/");
        assertThat(body).contains("alt=\"FM hero\"").contains("alt=\"Author\"");
        assertThat(section(body, "fm-hero")).contains("350w");
        assertThat(section(body, "fm-avatar")).contains("1x").contains("2x");
        assertThat(section(body, "fm-direct")).contains("/static/images/generated/").doesNotContain("<img");
        assertThat(section(body, "img-path")).contains("background-image: url(\"/static/images/generated/");
        assertThat(section(body, "img-url")).contains("/static/images/generated/");
    }

    @Test
    public void testNarrowPageImageStillHasASocialCard() {
        // cover.jpg is 800 wide, the roq-og preset asks for 1200: the variant is produced at the source width
        String body = get("/narrow/");
        int start = body.indexOf("property=\"og:image\" content=\"") + "property=\"og:image\" content=\"".length();
        String og = body.substring(start, body.indexOf('"', start));
        assertThat(og).contains("/static/images/generated/").contains("cover-800-");
        RestAssured.when().get(og).then().statusCode(200);
        assertThat(section(body, "narrow")).contains("600w").contains("800w").doesNotContain("1000w");
    }

    @Test
    public void testElvisFallsBackToPageImage() {
        assertThat(section(get("/expressions/"), "elvis")).contains("alt=\"Elvis\"").contains("gallery-food");
    }

    @Test
    public void testEncodedAttachmentName() {
        assertThat(section(get("/expressions/"), "encoded")).contains("alt=\"Encoded\"")
                .contains("/static/images/generated/").doesNotContain("Fo%C3%A9");
    }

    @Test
    public void testSharedImageAcrossPresets() {
        assertThat(get("/posts/first/")).contains("alt=\"First\"").contains("350w");
        assertThat(get("/posts/second/")).contains("alt=\"Second\"").contains("350w");
        assertThat(section(get("/expressions/"), "listing")).contains("alt=\"First\"").contains("alt=\"Second\"")
                .contains("160w");
    }

    @Test
    public void testGeneratedImagesServed() {
        for (String path : new String[] { "/image-test/", "/expressions/", "/posts/first/", "/posts/second/" }) {
            for (String generated : generatedPaths(get(path))) {
                RestAssured.when().get(generated).then().statusCode(200);
            }
        }
    }

    @Test
    public void testRemoteImagePassesThrough() {
        assertThat(section(get("/remote/"), "remote")).contains("<img src=\"https://example.com/remote.jpg\"")
                .doesNotContain("srcset");
    }

    @Test
    public void testVectorImagePassesThrough() {
        assertThat(section(get("/vector/"), "vector")).contains("<img src=\"/vector/logo.svg\"")
                .doesNotContain("srcset");
    }

    @Test
    public void testMissingImageRendersNothing() {
        assertThat(section(get("/no-image/"), "no-image")).doesNotContain("<img");
    }

    @Test
    public void testDraftIsNeitherPublishedNorConverted() throws IOException {
        RestAssured.when().get("/posts/draft/").then().statusCode(404);
        Path buildDir = Path.of("target/qute-image");
        assertThat(buildDir).isDirectory();
        try (Stream<Path> files = Files.walk(buildDir)) {
            assertThat(files.filter(p -> p.getFileName().toString().startsWith("draft-"))).isEmpty();
        }
    }
}
