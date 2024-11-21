package io.quarkiverse.roq.plugin.qrcode.runtime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.barcode.okapi.Okapi;
import io.quarkus.arc.Unremovable;
import uk.org.okapibarcode.backend.QrCode;
import uk.org.okapibarcode.graphics.Color;
import uk.org.okapibarcode.output.SvgRenderer;

@ApplicationScoped
@Unremovable
public class QRCodeRenderer {

    private final String rootPath;

    public QRCodeRenderer(@ConfigProperty(name = "quarkus.http.root-path") String rootPath) {
        this.rootPath = rootPath != "/" ? rootPath : "";
    }

    public String encode(String value, String alt, String foreground, String background, int width, int height,
            Boolean asciidoc) {
        // Create and configure QR code with low error correction level
        QrCode qrCode = new QrCode();
        qrCode.setContent(value);
        qrCode.setPreferredEccLevel(QrCode.EccLevel.L); // Low error correction provides smallest QR code size

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
        byte[] svgBytes = out.toByteArray();

        if (asciidoc) {
            // Render the SVG QR code as an Asciidoc image
            String fileName = String.format("qrcode-%s.svg", value.hashCode());
            return "image::%s/static/assets/images/%s[alt=\"%s\", width=%s,height=%s]".formatted(
                    rootPath,
                    renderQRCode(fileName, svgBytes),
                    alt,
                    width,
                    height);
        }
        String base64Image = Okapi.dataUriSvg(out.toByteArray());

        // Wrap the base64 image in an HTML img tag with specified dimensions
        String imgTag = String.format(
                "<img src=\"%s\" alt=\"%s\" width=\"%d\" height=\"%d\"/>",
                base64Image, alt, width, height);
        return imgTag;
    }

    private String renderQRCode(String fileName, byte[] content) {
        try {
            Path imagePath = Path.of("static/assets/images", fileName);
            Files.write(imagePath, content);
            return fileName;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
