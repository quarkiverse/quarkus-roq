package io.quarkiverse.roq.migration.react;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ImageSizeTest {

    @Test
    void readsTheSizeOfRasterImages() {
        ByteBuffer png = ByteBuffer.allocate(24).put(new byte[] { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10 })
                .putInt(13).put("IHDR".getBytes(StandardCharsets.US_ASCII)).putInt(200).putInt(100);
        assertThat(ImageSize.of("logo.png", png.array())).isEqualTo(new ImageSize(200, 100));

        ByteBuffer gif = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
                .put("GIF89a".getBytes(StandardCharsets.US_ASCII))
                .putShort((short) 300).putShort((short) 150);
        assertThat(ImageSize.of("a.gif", gif.array())).isEqualTo(new ImageSize(300, 150));

        // An APP0 segment, then a baseline start of frame with height 480 and width 640
        ByteBuffer jpeg = ByteBuffer.allocate(32).put(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 })
                .putShort((short) 4).putShort((short) 0).put(new byte[] { (byte) 0xFF, (byte) 0xC0 }).putShort((short) 17)
                .put((byte) 8).putShort((short) 480).putShort((short) 640);
        assertThat(ImageSize.of("photo.JPG", jpeg.array())).isEqualTo(new ImageSize(640, 480));

        ByteBuffer webp = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN).put("RIFF".getBytes(StandardCharsets.US_ASCII))
                .putInt(22).put("WEBPVP8X".getBytes(StandardCharsets.US_ASCII)).putInt(10).putInt(0)
                .put(new byte[] { (byte) 199, 0, 0, 99, 0, 0 });
        assertThat(ImageSize.of("a.webp", webp.array())).isEqualTo(new ImageSize(200, 100));
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
    }

    static ImageSize svg(String svg) {
        return ImageSize.of("logo.svg", svg.getBytes(StandardCharsets.UTF_8));
    }
}
