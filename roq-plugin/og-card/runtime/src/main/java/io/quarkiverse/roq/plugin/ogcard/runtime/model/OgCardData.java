package io.quarkiverse.roq.plugin.ogcard.runtime.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        int height,
        int maxTextWidth) {

    private static final int TITLE_FONT_SIZE = 52;
    private static final int DESCRIPTION_FONT_SIZE = 28;
    private static final double AVG_CHAR_WIDTH_RATIO = 0.52;
    private static final int TITLE_MAX_LINES = 2;
    private static final int DESCRIPTION_MAX_LINES = 4;
    private static final int DEFAULT_MARGIN = 144;

    public static OgCardData fromTarget(OgCardTarget target, int maxTextWidth) {
        return new OgCardData(
                xmlEscape(target.title()),
                xmlEscape(target.description()),
                xmlEscape(target.siteName()),
                xmlEscape(target.kicker()),
                xmlEscape(target.eyebrow()),
                target.width(),
                target.height(),
                maxTextWidth > 0 ? maxTextWidth : target.width() - DEFAULT_MARGIN);
    }

    public static OgCardData fromTarget(OgCardTarget target) {
        return fromTarget(target, -1);
    }

    public Map<String, Object> asTemplateData() {
        int charsPerLineTitle = estimateCharsPerLine(maxTextWidth, TITLE_FONT_SIZE);
        int charsPerLineDesc = estimateCharsPerLine(maxTextWidth, DESCRIPTION_FONT_SIZE);

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("description", description);
        data.put("siteName", siteName);
        data.put("kicker", kicker);
        data.put("eyebrow", eyebrow);
        data.put("width", width);
        data.put("height", height);
        data.put("titleLines", wrapText(title, charsPerLineTitle, TITLE_MAX_LINES));
        data.put("descriptionLines", wrapText(description, charsPerLineDesc, DESCRIPTION_MAX_LINES));
        return data;
    }

    static List<String> wrapText(String text, int maxCharsPerLine, int maxLines) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > maxCharsPerLine) {
                lines.add(line.toString());
                line = new StringBuilder();
                if (lines.size() >= maxLines) {
                    break;
                }
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty() && lines.size() < maxLines) {
            lines.add(line.toString());
        }

        if (lines.size() == maxLines) {
            String lastLine = lines.get(maxLines - 1);
            int wordsUsed = 0;
            for (String l : lines) {
                wordsUsed += l.split("\\s+").length;
            }
            if (wordsUsed < words.length) {
                if (lastLine.length() > maxCharsPerLine - 1) {
                    lastLine = lastLine.substring(0, maxCharsPerLine - 1).trim();
                }
                lines.set(maxLines - 1, lastLine + "\u2026");
            }
        }

        return lines;
    }

    private static int estimateCharsPerLine(int maxWidthPx, int fontSize) {
        return Math.max(10, (int) (maxWidthPx / (fontSize * AVG_CHAR_WIDTH_RATIO)));
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
