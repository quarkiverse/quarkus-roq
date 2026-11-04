package io.quarkiverse.roq.migration.react;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * The size of an image, read from its file: from its header with the JDK's ImageIO (PNG, JPEG, GIF, BMP), or from the
 * attributes of an SVG.
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
     * @return the size of the image; null if it is not a format read here, such as WebP, or the file does not say
     */
    public static ImageSize of(String fileName, byte[] bytes) {
        ImageSize size = fileName.toLowerCase(Locale.ROOT).endsWith(".svg")
                ? svg(new String(bytes, StandardCharsets.UTF_8))
                : raster(bytes);
        return size != null && size.width() > 0 && size.height() > 0 ? size : null;
    }

    private static ImageSize raster(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    return new ImageSize(reader.getWidth(0), reader.getHeight(0));
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException e) {
            // Its size is not known
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
}
