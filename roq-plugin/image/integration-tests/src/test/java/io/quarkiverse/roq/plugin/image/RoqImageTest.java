package io.quarkiverse.roq.plugin.image;

import static io.quarkiverse.roq.plugin.image.Pages.get;
import static io.quarkiverse.roq.plugin.image.Pages.section;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Literal image usages: absolute paths against public/, relative paths against the page directory.
 */
@QuarkusTest
public class RoqImageTest {

    @Test
    public void testPageRendersWithImages() {
        String body = get("/image-test/");
        assertThat(body).contains("<img").contains("srcset=").contains("/static/images/generated/");
        assertThat(body).contains("alt=\"Absolute ref\"").contains("alt=\"Page image\"");
    }

    @Test
    public void testPostCardPresetWidths() {
        assertThat(section(get("/image-test/"), "absolute")).contains("350w").contains("600w").contains("1000w");
    }

    @Test
    public void testPageLevelImage() {
        assertThat(section(get("/image-test/"), "page-image")).contains("alt=\"Page image\"").contains("400w")
                .contains("800w");
    }

    @Test
    public void testSiblingImage() {
        assertThat(section(get("/image-test/"), "sibling-image")).contains("alt=\"Sibling image\"")
                .contains("/static/images/generated/");
    }

    @Test
    public void testBuiltinPreset() {
        assertThat(section(get("/image-test/"), "builtin-preset")).contains("alt=\"Builtin preset\"")
                .contains("400w").contains("800w");
    }

    @Test
    public void testSingleFormatRendersImgNotPicture() {
        assertThat(section(get("/image-test/"), "single-format")).contains("<img").contains("srcset=")
                .contains("alt=\"Single format\"").doesNotContain("<picture");
    }
}
