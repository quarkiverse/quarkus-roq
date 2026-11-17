package io.quarkiverse.roq.migration.react;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class ImageSizeTest {

    @Test
    void readsTheSizeOfRasterImages() throws IOException {
        assertThat(ImageSize.of("logo.png", image("png", 200, 100))).isEqualTo(new ImageSize(200, 100));
        assertThat(ImageSize.of("a.gif", image("gif", 300, 150))).isEqualTo(new ImageSize(300, 150));
        assertThat(ImageSize.of("photo.JPG", image("jpg", 640, 480))).isEqualTo(new ImageSize(640, 480));
    }

    /**
     * @return an image of that format and size, as the JDK writes it
     */
    static byte[] image(String format, int width, int height) throws IOException {
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, image);
        return image.toByteArray();
    }

    @Test
    void readsTheSizeOfSvgImagesFromTheirDimensionsOrViewBox() {
        assertThat(svg("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"128px\" height='36'>"))
                .isEqualTo(new ImageSize(128, 36));
        assertThat(svg("<?xml version=\"1.0\"?>\n<svg width=\"100%\" viewBox=\"0 0 613 145.5\">"))
                .isEqualTo(new ImageSize(613, 145.5));
    }

    @Test
    void saysNothingAboutWhatItCannotRead() {
        assertThat(svg("<svg/>")).isNull();
        assertThat(ImageSize.of("a.avif", new byte[] { 0, 0, 0, 1 })).isNull();
        assertThat(ImageSize.of("broken.png", new byte[] { (byte) 0x89, 'P', 'N', 'G' })).isNull();
        // The JDK has no WebP reader
        assertThat(ImageSize.of("a.webp", "RIFF\u0016\u0000\u0000\u0000WEBPVP8X".getBytes(StandardCharsets.ISO_8859_1)))
                .isNull();
    }

    static ImageSize svg(String svg) {
        return ImageSize.of("logo.svg", svg.getBytes(StandardCharsets.UTF_8));
    }
}
