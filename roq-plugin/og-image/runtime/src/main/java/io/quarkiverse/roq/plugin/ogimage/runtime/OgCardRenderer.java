package io.quarkiverse.roq.plugin.ogimage.runtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import io.quarkiverse.roq.plugin.ogimage.runtime.model.OgCardData;
import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;

@ApplicationScoped
public class OgCardRenderer {

    private final OgImageConfig config;
    private final Engine engine;

    @Inject
    public OgCardRenderer(OgImageConfig config, Engine engine) {
        this.config = config;
        this.engine = engine;
    }

    public byte[] render(OgCardData card) {
        Template template = engine.getTemplate(config.template());
        if (template == null) {
            throw new IllegalStateException("OG image template not found: " + config.template());
        }
        String svg = template
                .data("card", card)
                .setAttribute("escape", false)
                .render();
        return renderSvg(svg, card.width(), card.height());
    }

    byte[] renderSvg(String svg, int width, int height) {
        return svgToPng(svg, width, height);
    }

    private byte[] svgToPng(String svg, int width, int height) {
        try (InputStream input = new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) width);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, (float) height);
            transcoder.transcode(new TranscoderInput(input), new TranscoderOutput(output));
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render OG image", e);
        }
    }
}
