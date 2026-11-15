package org.vnk.healthmonitor.functional;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end image compression: a large uploaded PNG is stored as a
 * downscaled JPEG, so a history of scans stays far below MongoDB's
 * 16 MB document limit.
 */
class ImageCompressionFunctionalTest extends FunctionalTestBase {

    private static final String VISUAL = "oct_fundus_scan";

    @Test
    void uploadedImagesAreStoredAsDownscaledJpegs() throws Exception {
        String token = createAccount(uniqueUsername());

        // A 1600x1200 noise PNG: noise compresses badly in PNG, so the
        // upload is genuinely large (several MB) while a 1024px JPEG of
        // the same content is a couple hundred KB at most.
        BufferedImage upload = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(7);
        for (int y = 0; y < upload.getHeight(); y++) {
            for (int x = 0; x < upload.getWidth(); x++) {
                upload.setRGB(x, y, random.nextInt());
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(upload, "png", png);
        byte[] uploadBytes = png.toByteArray();
        String uploadBase64 = Base64.getEncoder().encodeToString(uploadBytes);
        assertTrue(uploadBytes.length < 16 * 1024 * 1024,
                "Test precondition: the upload fits the 16 MB limit");

        HttpResponse<String> log = send("POST", "/users/measurements/" + VISUAL,
                "{\"image\":\"" + uploadBase64 + "\"}", authHeader(token));
        assertEquals(200, log.statusCode());

        HttpResponse<String> history = send("GET", "/users/measurements/" + VISUAL,
                null, authHeader(token));
        assertEquals(200, history.statusCode());
        List<MeasurementSetDto> sets = parseList(history.body(), MeasurementSetDto.class);
        String storedBase64 = sets.get(0).images().get(0);

        byte[] storedBytes = Base64.getDecoder().decode(storedBase64);
        assertTrue(storedBytes.length < uploadBytes.length,
                "The stored image must be smaller than the upload: "
                        + storedBytes.length + " vs " + uploadBytes.length);
        assertTrue(storedBytes.length <= 256 * 1024,
                "The stored image must fit the 256 KB cap, was " + storedBytes.length);

        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(storedBytes));
        assertNotNull(stored, "The stored image must still decode");
        assertEquals(1024, stored.getWidth());
        assertEquals(768, stored.getHeight());
    }
}
