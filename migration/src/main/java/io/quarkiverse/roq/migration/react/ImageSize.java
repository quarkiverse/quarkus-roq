package io.quarkiverse.roq.migration.react;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The size of an image, read from its file: PNG, JPEG, GIF, WebP or SVG.
 */
public record ImageSize(double width, double height) {

    static final Pattern SVG_TAG = Pattern.compile("<svg\\b[^>]*>", Pattern.DOTALL);
    static final Pattern SVG_LENGTH = Pattern.compile("\\s*(\\d+(?:\\.\\d+)?)\\s*(?:px)?\\s*");
    static final Pattern VIEW_BOX = Pattern.compile(
            "\\s*-?[\\d.]+[\\s,]+-?[\\d.]+[\\s,]+(\\d+(?:\\.\\d+)?)[\\s,]+(\\d+(?:\\.\\d+)?)\\s*");

    public double aspectRatio() {
        return width / height;
    }

    /**
     * @return the size of the image; null if it is not a format read here, or the file does not say
     */
    public static ImageSize of(String fileName, byte[] bytes) {
        String name = fileName.toLowerCase(Locale.ROOT);
        try {
            ImageSize size = name.endsWith(".svg") ? svg(new String(bytes, StandardCharsets.UTF_8)) : raster(bytes);
            return size != null && size.width() > 0 && size.height() > 0 ? size : null;
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private static ImageSize raster(byte[] b) {
        ByteBuffer big = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN);
        ByteBuffer little = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        if (startsWith(b, 0, 0x89, 'P', 'N', 'G')) {
            return new ImageSize(big.getInt(16), big.getInt(20));
        }
        if (startsWith(b, 0, 'G', 'I', 'F')) {
            return new ImageSize(little.getShort(6) & 0xFFFF, little.getShort(8) & 0xFFFF);
        }
        if (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P')) {
            return webp(b, little);
        }
        if (startsWith(b, 0, 0xFF, 0xD8)) {
            return jpeg(b, big);
        }
        return null;
    }

    private static ImageSize webp(byte[] b, ByteBuffer little) {
        if (startsWith(b, 12, 'V', 'P', '8', ' ')) {
            return new ImageSize(little.getShort(26) & 0x3FFF, little.getShort(28) & 0x3FFF);
        }
        if (startsWith(b, 12, 'V', 'P', '8', 'L')) {
            int bits = little.getInt(21);
            return new ImageSize((bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1);
        }
        if (startsWith(b, 12, 'V', 'P', '8', 'X')) {
            return new ImageSize(uint24(b, 24) + 1, uint24(b, 27) + 1);
        }
        return null;
    }

    private static ImageSize jpeg(byte[] b, ByteBuffer big) {
        int i = 2;
        while (i + 9 < b.length) {
            if ((b[i] & 0xFF) != 0xFF) {
                return null;
            }
            int marker = b[i + 1] & 0xFF;
            // Start of frame markers, apart from DHT (C4), JPG (C8) and DAC (CC)
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                return new ImageSize(big.getShort(i + 7) & 0xFFFF, big.getShort(i + 5) & 0xFFFF);
            }
            i += 2 + (big.getShort(i + 2) & 0xFFFF);
        }
        return null;
    }

    /**
     * @return the size from the width and height attributes when they are in pixels, else from the viewBox
     */
    private static ImageSize svg(String svg) {
        Matcher tag = SVG_TAG.matcher(svg);
        if (!tag.find()) {
            return null;
        }
        String width = attribute(tag.group(), "width");
        String height = attribute(tag.group(), "height");
        Matcher w = width == null ? null : SVG_LENGTH.matcher(width);
        Matcher h = height == null ? null : SVG_LENGTH.matcher(height);
        if (w != null && w.matches() && h != null && h.matches()) {
            return new ImageSize(Double.parseDouble(w.group(1)), Double.parseDouble(h.group(1)));
        }
        String viewBox = attribute(tag.group(), "viewBox");
        Matcher box = viewBox == null ? null : VIEW_BOX.matcher(viewBox);
        if (box != null && box.matches()) {
            return new ImageSize(Double.parseDouble(box.group(1)), Double.parseDouble(box.group(2)));
        }
        return null;
    }

    private static String attribute(String tag, String name) {
        Matcher attribute = Pattern.compile("\\s" + name + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')").matcher(tag);
        return attribute.find() ? (attribute.group(1) != null ? attribute.group(1) : attribute.group(2)) : null;
    }

    private static boolean startsWith(byte[] b, int offset, int... prefix) {
        if (b.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((b[offset + i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int uint24(byte[] b, int offset) {
        return (b[offset] & 0xFF) | (b[offset + 1] & 0xFF) << 8 | (b[offset + 2] & 0xFF) << 16;
    }
}
