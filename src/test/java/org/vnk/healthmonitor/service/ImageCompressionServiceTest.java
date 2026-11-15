package org.vnk.healthmonitor.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the server-side image compression: downscaling, JPEG
 * re-encoding, the size/quality ladders, and the rejection paths. Real
 * images are built and encoded in-test with ImageIO.
 */
class ImageCompressionServiceTest {

    private static final int DEFAULT_MAX_DIMENSION = 1024;
    private static final int DEFAULT_MAX_STORED_BYTES = 256 * 1024;

    private ImageCompressionService service(int maxStoredBytes, int maxUploadBytes) {
        return new ImageCompressionService(DEFAULT_MAX_DIMENSION, 0.7, 0.3,
                maxStoredBytes, maxUploadBytes);
    }

    private ImageCompressionService defaultService() {
        return service(DEFAULT_MAX_STORED_BYTES, 16 * 1024 * 1024);
    }

    private BufferedImage image(int width, int height, boolean noise) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        if (noise) {
            Random random = new Random(42);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    graphics.setColor(new Color(random.nextInt(256), random.nextInt(256),
                            random.nextInt(256)));
                    graphics.fillRect(x, y, 1, 1);
                }
            }
        } else {
            // A horizontal gradient: smooth, so it compresses well.
            for (int x = 0; x < width; x++) {
                graphics.setColor(new Color(x * 255 / Math.max(1, width - 1), 100, 150));
                graphics.fillRect(x, 0, 1, height);
            }
        }
        graphics.dispose();
        return image;
    }

    private String toBase64Png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private BufferedImage decode(String base64) {
        try {
            return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void largeImageIsDownscaledToMaxDimensionKeepingAspectRatio() {
        String compressed = defaultService().compress(toBase64Png(image(2000, 1000, false)));

        BufferedImage output = decode(compressed);
        assertEquals(1024, output.getWidth());
        assertEquals(512, output.getHeight());
    }

    @Test
    void portraitImagesScaleByTheirLongestSide() {
        String compressed = defaultService().compress(toBase64Png(image(800, 2400, false)));

        BufferedImage output = decode(compressed);
        // Longest side (2400) is scaled to 1024: 800 * 1024 / 2400 = 341.
        assertEquals(341, output.getWidth());
        assertEquals(1024, output.getHeight());
    }

    @Test
    void smallImagesKeepTheirSize() {
        String compressed = defaultService().compress(toBase64Png(image(640, 400, false)));

        BufferedImage output = decode(compressed);
        assertEquals(640, output.getWidth());
        assertEquals(400, output.getHeight());
    }

    @Test
    void outputIsACompressedJpeg() {
        String upload = toBase64Png(image(3000, 2000, false));
        String compressed = defaultService().compress(upload);

        byte[] stored = Base64.getDecoder().decode(compressed);
        assertTrue(stored.length < Base64.getDecoder().decode(upload).length,
                "A smooth gradient PNG should compress to less");
        assertTrue(stored.length <= DEFAULT_MAX_STORED_BYTES,
                "Stored image must fit the cap, was " + stored.length);
    }

    @Test
    void transparencyFlattensToWhite() {
        BufferedImage transparent = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
        String compressed = defaultService().compress(toBase64Png(transparent));

        BufferedImage output = decode(compressed);
        assertFalse(output.getColorModel().hasAlpha(), "JPEG output has no alpha channel");
        // JPEG lossily encodes the white backing, so allow a little drift.
        Color corner = new Color(output.getRGB(0, 0));
        assertTrue(corner.getRed() > 200 && corner.getGreen() > 200 && corner.getBlue() > 200,
                "Transparent areas flatten to white, was " + corner);
    }

    @Test
    void dataUriPrefixIsStripped() {
        String compressed = defaultService().compress(
                "data:image/png;base64," + toBase64Png(image(200, 100, false)));

        assertEquals(200, decode(compressed).getWidth());
    }

    @Test
    void garbageBase64IsRejected() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> defaultService().compress("this is not base64!!!"));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void decodableButNonImageBytesAreRejected() {
        // "hello" is valid base64 but not a picture.
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> defaultService().compress("aGVsbG8="));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void uploadsOver16MBAreRejectedBeforeDecoding() {
        // 17 MB of base64 ('A' characters) is over the line even though
        // it would decode fine.
        String huge = "A".repeat(17 * 1024 * 1024 * 4 / 3);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> defaultService().compress(huge));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertTrue(e.getMessage().contains("upload limit"));
    }

    @Test
    void uploadsAtOrUnder16MBAreAcceptedForProcessing() {
        // Under the limit but not a picture: rejected as undecodable, not
        // as too large — proving the size guard is not the gate.
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> defaultService().compress("A".repeat(1000)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertFalse(String.valueOf(e.getMessage()).contains("upload limit"));
    }

    @Test
    void incompressibleImageIsRejected() {
        // Pure noise compresses badly; a 100-byte cap cannot be met even
        // at 256 px and the quality floor.
        String noisy = toBase64Png(image(512, 512, true));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service(100, 16 * 1024 * 1024).compress(noisy));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertTrue(e.getMessage().contains("cannot be compressed small enough"),
                "Unexpected message: " + e.getMessage());
    }

    @Test
    void nullImageIsRejected() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> defaultService().compress(null));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }
}
