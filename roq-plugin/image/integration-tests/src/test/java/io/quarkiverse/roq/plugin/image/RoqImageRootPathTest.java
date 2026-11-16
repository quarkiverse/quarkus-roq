package io.quarkiverse.roq.plugin.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Under a non root {@code quarkus.http.root-path}, {@code img:url(...).absolute} carries the site url and the
 * root path exactly once.
 */
@QuarkusTest
@TestProfile(RoqImageRootPathTest.Profile.class)
public class RoqImageRootPathTest {

    @Test
    public void testImgUrlAbsoluteWithRootPath() {
        String span = Pages.section(Pages.get("/expressions/"), "img-url");
        String url = span.substring(span.indexOf("<span>") + "<span>".length(), span.indexOf("</span>")).trim();
        assertThat(url).startsWith("https://example.com/blog/static/images/generated/");
        assertThat(url.indexOf("/blog/")).isEqualTo(url.lastIndexOf("/blog/"));
    }

    @Test
    public void testImgTagAndPathCarryTheRootPathOnce() {
        String body = Pages.get("/expressions/");
        String hero = Pages.section(body, "fm-hero");
        String src = attr(hero, "src");
        assertThat(src).startsWith("/blog/static/images/generated/");
        assertThat(src.indexOf("/blog/")).isEqualTo(src.lastIndexOf("/blog/"));
        for (String candidate : attr(hero, "srcset").split(",")) {
            assertThat(candidate.trim()).startsWith("/blog/static/images/generated/");
        }
        assertThat(Pages.section(body, "img-path"))
                .contains("background-image: url(\"/blog/static/images/generated/");
    }

    @Test
    public void testPassthroughSocialImageCarriesTheRootPathOnce() {
        // an SVG page image is sent as it is: its url must not get the root path twice
        String body = Pages.get("/vector/");
        assertThat(body).contains("property=\"og:image\" content=\"https://example.com/blog/vector/logo.svg\"");
    }

    private static String attr(String html, String name) {
        int start = html.indexOf(name + "=\"") + name.length() + 2;
        return html.substring(start, html.indexOf('"', start));
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.http.root-path", "/blog", "site.url", "https://example.com");
        }
    }
}
