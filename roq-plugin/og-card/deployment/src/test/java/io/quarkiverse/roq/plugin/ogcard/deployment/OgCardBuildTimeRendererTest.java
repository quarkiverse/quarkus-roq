package io.quarkiverse.roq.plugin.ogcard.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.plugin.ogcard.runtime.model.OgCardData;
import io.quarkiverse.roq.plugin.ogcard.runtime.model.OgCardTarget;
import io.quarkus.qute.Engine;

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

        String svg = engine.getTemplate("og-card/default-card.svg")
                .data("card", card.asTemplateData())
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

}
