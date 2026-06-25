package io.quarkiverse.roq.plugin.ogimage.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.roq.plugin.ogimage.runtime.model.OgCardData;
import io.quarkiverse.roq.plugin.ogimage.runtime.model.OgImageRegistry;
import io.quarkiverse.roq.plugin.ogimage.runtime.model.OgImageTarget;

@ApplicationScoped
public class OgImageService {

    private final OgImageRegistry registry;
    private final OgCardRenderer renderer;
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    @Inject
    public OgImageService(OgImageRegistry registry, OgCardRenderer renderer) {
        this.registry = registry;
        this.renderer = renderer;
    }

    public byte[] renderPng(String pngPath) {
        OgImageTarget target = registry.findByPngPath(pngPath);
        if (target == null) {
            return null;
        }
        return renderTarget(target);
    }

    public byte[] renderTarget(OgImageTarget target) {
        return cache.computeIfAbsent(target.pngPath(), ignored -> renderer.render(OgCardData.fromTarget(target)));
    }
}
