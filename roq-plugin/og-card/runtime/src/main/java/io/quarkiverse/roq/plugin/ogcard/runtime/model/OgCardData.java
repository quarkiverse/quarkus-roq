package io.quarkiverse.roq.plugin.ogcard.runtime.model;

import java.util.Map;

/**
 * Data passed to the OG card Qute template.
 */
public record OgCardData(
        String title,
        String description,
        String siteName,
        String kicker,
        String eyebrow,
        int width,
        int height) {

    public static OgCardData fromTarget(OgCardTarget target) {
        return new OgCardData(
                xmlEscape(target.title()),
                xmlEscape(target.description()),
                xmlEscape(target.siteName()),
                xmlEscape(target.kicker()),
                xmlEscape(target.eyebrow()),
                target.width(),
                target.height());
    }

    public Map<String, Object> asTemplateData() {
        return Map.of(
                "title", title,
                "description", description,
                "siteName", siteName,
                "kicker", kicker,
                "eyebrow", eyebrow,
                "width", width,
                "height", height);
    }

    private static String xmlEscape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
