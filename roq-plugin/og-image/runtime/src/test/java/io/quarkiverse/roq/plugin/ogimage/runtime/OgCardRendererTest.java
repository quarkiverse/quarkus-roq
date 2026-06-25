package io.quarkiverse.roq.plugin.ogimage.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.plugin.ogimage.runtime.model.OgCardData;
import io.quarkiverse.roq.plugin.ogimage.runtime.model.OgImageTarget;
import io.quarkus.qute.Engine;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

class OgCardRendererTest {

    @Test
    void rendersSvgToPng() {
        OgImageConfig config = config(
                "quarkus.roq.plugin.og-image.template=og-image/default-card.svg",
                "quarkus.roq.plugin.og-image.width=1200",
                "quarkus.roq.plugin.og-image.height=630");

        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630">
                  <rect width="1200" height="630" fill="#0b1220"/>
                  <text x="72" y="170" fill="#ffffff" font-family="Arial" font-size="52">Start here with Bob</text>
                </svg>
                """;

        OgCardRenderer renderer = new OgCardRenderer(config, Engine.builder().build());
        byte[] png = renderer.renderSvg(svg, 1200, 630);

        assertThat(png).isNotEmpty();
        assertThat(png[0]).isEqualTo((byte) 0x89);
        assertThat(png[1]).isEqualTo((byte) 'P');
        assertThat(png[2]).isEqualTo((byte) 'N');
        assertThat(png[3]).isEqualTo((byte) 'G');
    }

    @Test
    void rendersDefaultCardTemplateWithXmlEscapedTitle() throws IOException {
        OgImageConfig config = config(
                "quarkus.roq.plugin.og-image.template=og-image/default-card.svg",
                "quarkus.roq.plugin.og-image.width=1200",
                "quarkus.roq.plugin.og-image.height=630",
                "quarkus.roq.plugin.og-image.site-name=Test & Co");

        Engine engine = Engine.builder().addDefaults().build();
        registerDefaultCardTemplate(engine);

        OgCardData card = OgCardData.fromTarget(new OgImageTarget(
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

        String svg = engine.getTemplate("og-image/default-card.svg")
                .data("card", cardData)
                .setAttribute("escape", false)
                .render();

        assertThat(svg).contains("Foo &amp; Bar");

        OgCardRenderer renderer = new OgCardRenderer(config, engine);
        byte[] png = renderer.renderSvg(svg, 1200, 630);

        assertThat(png).isNotEmpty();
        assertPngDimensions(png, 1200, 630);
    }

    @Test
    void outputDimensionsMatchTranscoderHints() throws IOException {
        OgImageConfig config = config(
                "quarkus.roq.plugin.og-image.template=og-image/default-card.svg",
                "quarkus.roq.plugin.og-image.width=1200",
                "quarkus.roq.plugin.og-image.height=630");

        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630">
                  <rect width="1200" height="630" fill="#0b1220"/>
                </svg>
                """;

        OgCardRenderer renderer = new OgCardRenderer(config, Engine.builder().build());
        byte[] png = renderer.renderSvg(svg, 1200, 630);

        assertPngDimensions(png, 1200, 630);
    }

    private static void registerDefaultCardTemplate(Engine engine) throws IOException {
        try (InputStream in = OgCardRendererTest.class.getClassLoader()
                .getResourceAsStream("templates/og-image/default-card.svg")) {
            assertThat(in).isNotNull();
            String template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            engine.putTemplate("og-image/default-card.svg", engine.parse(template));
        }
    }

    private static void assertPngDimensions(byte[] png, int width, int height) throws IOException {
        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(width);
        assertThat(image.getHeight()).isEqualTo(height);
    }

    private static OgImageConfig config(String... keyValues) {
        SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder()
                .withMapping(OgImageConfig.class)
                .withValidateUnknown(false);
        for (String keyValue : keyValues) {
            int idx = keyValue.indexOf('=');
            builder.withDefaultValue(keyValue.substring(0, idx), keyValue.substring(idx + 1));
        }
        SmallRyeConfig smallRyeConfig = builder.build();
        return smallRyeConfig.getConfigMapping(OgImageConfig.class);
    }
}
