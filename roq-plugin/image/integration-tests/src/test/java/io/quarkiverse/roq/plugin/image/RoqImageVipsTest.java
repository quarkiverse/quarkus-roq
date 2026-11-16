package io.quarkiverse.roq.plugin.image;

import static io.quarkiverse.roq.plugin.image.Pages.generatedPaths;
import static io.quarkiverse.roq.plugin.image.Pages.get;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

/** With the vips extension, presets listing webp render a picture with a webp source and a jpg fallback. */
@QuarkusTest
public class RoqImageVipsTest {

    @Test
    public void testPictureWithWebpSourceAndImgFallback() {
        String body = get("/image-test/");
        int start = body.indexOf("<picture");
        assertThat(start).isNotNegative();
        String picture = body.substring(start, body.indexOf("</picture>", start));
        assertThat(picture).contains("<source").contains("image/webp").contains("<img");
    }

    @Test
    public void testWebpVariantsServed() {
        List<String> webp = generatedPaths(get("/image-test/")).stream().filter(p -> p.endsWith(".webp")).toList();
        assertThat(webp).as("at least one webp variant").isNotEmpty();
        for (String path : webp) {
            RestAssured.when().get(path).then().statusCode(200);
        }
    }
}
