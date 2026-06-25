package io.quarkiverse.roq.plugin.ogimage.runtime.model;

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

    public static OgCardData fromTarget(OgImageTarget target) {
        return new OgCardData(
                xmlEscape(target.title()),
                xmlEscape(target.description()),
                xmlEscape(target.siteName()),
                xmlEscape(target.kicker()),
                xmlEscape(target.eyebrow()),
                target.width(),
                target.height());
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
