package io.quarkiverse.roq.plugin.ogcard.deployment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import io.quarkiverse.roq.plugin.ogcard.runtime.OgCardConfig;
import io.quarkiverse.roq.plugin.ogcard.runtime.model.OgCardData;
import io.quarkiverse.roq.plugin.ogcard.runtime.model.OgCardTarget;
import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.qute.deployment.TemplatePathBuildItem;

final class OgCardBuildTimeRenderer {

    private OgCardBuildTimeRenderer() {
    }

    static Engine createEngine(OgCardConfig config, List<TemplatePathBuildItem> templatePaths) {
        Engine engine = Engine.builder().addDefaults().build();
        String templateId = config.template();

        Optional<TemplatePathBuildItem> selected = templatePaths.stream()
                .filter(item -> templateId.equals(item.getPath()))
                .max(Comparator.comparingInt(TemplatePathBuildItem::getPriority));

        String content = selected.map(TemplatePathBuildItem::getContent)
                .orElseGet(() -> loadBuiltinTemplate(templateId));
        engine.putTemplate(templateId, engine.parse(content));
        return engine;
    }

    static byte[] render(OgCardConfig config, Engine engine, OgCardTarget target) {
        OgCardData card = OgCardData.fromTarget(target, config.maxTextWidth());
        Template template = engine.getTemplate(config.template());
        if (template == null) {
            throw new IllegalStateException("OG card template not found: " + config.template());
        }
        String svg = template
                .data("card", card.asTemplateData())
                .setAttribute("escape", false)
                .render();
        return svgToPng(svg, card.width(), card.height());
    }

    static byte[] renderSvg(String svg, int width, int height) {
        return svgToPng(svg, width, height);
    }

    private static byte[] svgToPng(String svg, int width, int height) {
        try (InputStream input = new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) width);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, (float) height);
            transcoder.transcode(new TranscoderInput(input), new TranscoderOutput(output));
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render OG card", e);
        }
    }

    private static String loadBuiltinTemplate(String templateId) {
        String resourcePath = "templates/" + templateId;
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("OG card template not found: " + templateId);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load OG card template: " + templateId, e);
        }
    }

    static void validateTemplate(OgCardConfig config, Engine engine) {
        Template template = engine.getTemplate(config.template());
        if (template == null) {
            throw new IllegalStateException("OG card template not found: " + config.template());
        }
    }
}
