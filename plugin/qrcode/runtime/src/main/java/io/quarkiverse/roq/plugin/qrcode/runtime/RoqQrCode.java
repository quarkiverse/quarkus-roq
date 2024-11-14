package io.quarkiverse.roq.plugin.qrcode.runtime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import io.quarkiverse.barcode.okapi.Okapi;
import io.quarkus.qute.EngineConfiguration;
import uk.org.okapibarcode.backend.QrCode;
import uk.org.okapibarcode.backend.QrCode.EccLevel;
import uk.org.okapibarcode.graphics.Color;
import uk.org.okapibarcode.output.SvgRenderer;

/**
 * Qute template section helper for generating QR codes.
 * Extends QuteBarCode to provide QR code generation functionality using the Okapi library.
 */
@EngineConfiguration
public class RoqQrCode extends QuteBarCode {

    /**
     * Constructs a new RoqQrCode instance.
     * Registers the "qrcode" section helper with the encode method.
     */
    public RoqQrCode() {
        super("qrcode", RoqQrCode::encode);
    }

    /**
     * Encodes a QR code as an SVG image and returns it wrapped in an HTML img tag.
     *
     * @param value The content to encode in the QR code
     * @param alt The alt text for the generated image
     * @param foreground The foreground color (hex format with optional # prefix)
     * @param background The background color (hex format with optional # prefix)
     * @param width The width of the generated image in pixels
     * @param height The height of the generated image in pixels
     * @return An HTML img tag containing the base64-encoded SVG QR code
     */
    private static String encode(String value, String alt, String foreground, String background, int width, int height) {
        // Create and configure QR code with low error correction level
        QrCode qrCode = new QrCode();
        qrCode.setContent(value);
        qrCode.setPreferredEccLevel(EccLevel.L); // Low error correction provides smallest QR code size

        // Strip leading # from hex color codes if present
        if (foreground.startsWith("#")) {
            foreground = foreground.substring(1);
        }
        if (background.startsWith("#")) {
            background = background.substring(1);
        }

        // Generate SVG representation of the QR code
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SvgRenderer renderer = new SvgRenderer(out, 1.0, new Color(background), new Color(foreground), true);
        try {
            renderer.render(qrCode);
        } catch (IOException e) {
            throw new RuntimeException("Error rendering QR code", e);
        }

        // Convert SVG to base64 data URI format
        String base64Image = Okapi.dataUriSvg(out.toByteArray());

        // Wrap the base64 image in an HTML img tag with specified dimensions
        String imgTag = String.format(
                "<img src=\"%s\" alt=\"%s\" width=\"%d\" height=\"%d\"/>",
                base64Image, alt, width, height);
        return imgTag;
    }

}
