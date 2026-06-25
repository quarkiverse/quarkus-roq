package io.quarkiverse.roq.plugin.ogimage.runtime.model;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.Unremovable;

@ApplicationScoped
@Unremovable
public class OgImageRegistry {

    private final Map<String, OgImageTarget> targetsByPngPath;

    public OgImageRegistry(Map<String, OgImageTarget> targetsByPngPath) {
        this.targetsByPngPath = Map.copyOf(targetsByPngPath);
    }

    public OgImageTarget findByPngPath(String pngPath) {
        if (pngPath == null) {
            return null;
        }
        String normalized = pngPath.endsWith("/") ? pngPath.substring(0, pngPath.length() - 1) : pngPath;
        OgImageTarget target = targetsByPngPath.get(normalized);
        if (target != null) {
            return target;
        }
        if (!normalized.startsWith("/")) {
            return targetsByPngPath.get("/" + normalized);
        }
        return null;
    }

    public Map<String, OgImageTarget> targetsByPngPath() {
        return targetsByPngPath;
    }
}
