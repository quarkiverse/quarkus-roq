package io.quarkiverse.roq.plugin.ogcard.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.plugin.ogcard.runtime.OgCardConfig;
import io.quarkiverse.roq.plugin.ogcard.runtime.model.OgCardData;
import io.quarkiverse.roq.plugin.ogcard.runtime.model.OgCardTarget;
import io.quarkus.qute.Engine;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

class OgCardBuildTimeRendererTest {

    @Test
    void rendersSvgToPng() {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630">
                  <rect width="1200" height="630" fill="#0b1220"/>
                  <text x="72" y="170" fill="#ffffff" font-family="Arial" font-size="52">Start here with Bob</text>
                </svg>
                """;

        byte[] png = OgCardBuildTimeRenderer.renderSvg(svg, 1200, 630);

        assertThat(png).isNotEmpty();
        assertThat(png[0]).isEqualTo((byte) 0x89);
        assertThat(png[1]).isEqualTo((byte) 'P');
        assertThat(png[2]).isEqualTo((byte) 'N');
        assertThat(png[3]).isEqualTo((byte) 'G');
    }

    @Test
    void rendersDefaultCardTemplateWithXmlEscapedTitle() throws IOException {
        OgCardConfig config = config(
                "quarkus.roq.plugin.og-card.template=og-card/default-card.svg",
                "quarkus.roq.plugin.og-card.width=1200",
                "quarkus.roq.plugin.og-card.height=630",
                "quarkus.roq.plugin.og-card.site-name=Test & Co");

        Engine engine = Engine.builder().addDefaults().build();
        registerDefaultCardTemplate(engine);

        OgCardData card = OgCardData.fromTarget(new OgCardTarget(
                "/og/test.png",
                "test.md",
                "Foo & Bar",
                "Description with <tags>",
                "Test & Co",
                "Kicker",
                "",
                "Test & Co — Foo & Bar",
                1200,
                630));

        assertThat(card.title()).isEqualTo("Foo &amp; Bar");
        assertThat(card.description()).isEqualTo("Description with &lt;tags&gt;");

        Map<String, Object> cardData = Map.of(
                "title", card.title(),
                "description", card.description(),
                "siteName", card.siteName(),
                "kicker", card.kicker(),
                "eyebrow", card.eyebrow());

        String svg = engine.getTemplate("og-card/default-card.svg")
                .data("card", cardData)
                .setAttribute("escape", false)
                .render();

        assertThat(svg).contains("Foo &amp; Bar");

        byte[] png = OgCardBuildTimeRenderer.renderSvg(svg, 1200, 630);

        assertThat(png).isNotEmpty();
        assertPngDimensions(png, 1200, 630);
    }

    @Test
    void outputDimensionsMatchTranscoderHints() throws IOException {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630">
                  <rect width="1200" height="630" fill="#0b1220"/>
                </svg>
                """;

        byte[] png = OgCardBuildTimeRenderer.renderSvg(svg, 1200, 630);

        assertPngDimensions(png, 1200, 630);
    }

    private static void registerDefaultCardTemplate(Engine engine) throws IOException {
        try (InputStream in = OgCardBuildTimeRendererTest.class.getClassLoader()
                .getResourceAsStream("templates/og-card/default-card.svg")) {
            assertThat(in).isNotNull();
            String template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            engine.putTemplate("og-card/default-card.svg", engine.parse(template));
        }
    }

    private static void assertPngDimensions(byte[] png, int width, int height) throws IOException {
        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(width);
        assertThat(image.getHeight()).isEqualTo(height);
    }

    private static OgCardConfig config(String... keyValues) {
        SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder()
                .withMapping(OgCardConfig.class)
                .withValidateUnknown(false);
        for (String keyValue : keyValues) {
            int idx = keyValue.indexOf('=');
            builder.withDefaultValue(keyValue.substring(0, idx), keyValue.substring(idx + 1));
        }
        SmallRyeConfig smallRyeConfig = builder.build();
        return smallRyeConfig.getConfigMapping(OgCardConfig.class);
    }
}
