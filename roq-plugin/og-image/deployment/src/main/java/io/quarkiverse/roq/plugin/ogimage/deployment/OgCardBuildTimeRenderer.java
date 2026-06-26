package io.quarkiverse.roq.plugin.ogimage.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import io.quarkiverse.roq.plugin.ogimage.runtime.OgCardRenderer;
import io.quarkiverse.roq.plugin.ogimage.runtime.OgImageConfig;
import io.quarkiverse.roq.plugin.ogimage.runtime.model.OgCardData;
import io.quarkiverse.roq.plugin.ogimage.runtime.model.OgImageTarget;
import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.qute.deployment.TemplatePathBuildItem;

final class OgCardBuildTimeRenderer {

    private OgCardBuildTimeRenderer() {
    }

    static Engine createEngine(OgImageConfig config, List<TemplatePathBuildItem> templatePaths) {
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

    static byte[] render(OgImageConfig config, Engine engine, OgImageTarget target) {
        OgCardRenderer renderer = new OgCardRenderer(config, engine);
        return renderer.render(OgCardData.fromTarget(target));
    }

    private static String loadBuiltinTemplate(String templateId) {
        String resourcePath = "templates/" + templateId;
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("OG image template not found: " + templateId);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load OG image template: " + templateId, e);
        }
    }

    static void validateTemplate(OgImageConfig config, Engine engine) {
        Template template = engine.getTemplate(config.template());
        if (template == null) {
            throw new IllegalStateException("OG image template not found: " + config.template());
        }
    }
}
