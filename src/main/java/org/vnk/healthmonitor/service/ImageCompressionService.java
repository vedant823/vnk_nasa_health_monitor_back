package org.vnk.healthmonitor.service;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Compresses client-uploaded visual measurement images before they are
 * stored inline in the user document, so a history of scans stays well
 * under MongoDB's 16 MB document limit.
 *
 * The raw upload is accepted up to {@code max-upload-bytes} (16 MB);
 * anything smaller is downscaled to {@code max-dimension} on its longest
 * side and re-encoded as JPEG, with the quality lowered until the result
 * fits {@code max-stored-bytes}. If even the minimum size at the quality
 * floor is too big, the dimensions are halved and the quality ladder
 * runs again; only when even a 256 px minimum-quality encode cannot fit
 * is the upload rejected. The original is discarded — only the
 * compressed image is stored.
 */
@Service
public class ImageCompressionService {

    /** Smallest longest side the size ladder falls back to. */
    private static final int MIN_DIMENSION = 256;

    /** Quality step between re-encode attempts. */
    private static final double QUALITY_STEP = 0.15;

    private final int maxDimension;
    private final double quality;
    private final double qualityFloor;
    private final int maxStoredBytes;
    private final int maxUploadBytes;

    public ImageCompressionService(
            @Value("${app.images.max-dimension:1024}") int maxDimension,
            @Value("${app.images.quality:0.7}") double quality,
            @Value("${app.images.quality-floor:0.3}") double qualityFloor,
            @Value("${app.images.max-stored-bytes:262144}") int maxStoredBytes,
            @Value("${app.images.max-upload-bytes:16777216}") int maxUploadBytes) {
        this.maxDimension = maxDimension;
        this.quality = quality;
        this.qualityFloor = qualityFloor;
        this.maxStoredBytes = maxStoredBytes;
        this.maxUploadBytes = maxUploadBytes;
    }

    /**
     * Compresses one base64 image (with or without a data-URI prefix)
     * into a stored-size JPEG, returned as bare base64.
     */
    public String compress(String base64Image) {
        String raw = stripDataUriPrefix(base64Image);

        // Cheap guard before decoding: base64 inflates by ~4/3.
        if (raw.length() > (long) maxUploadBytes * 4 / 3 + 4) {
            throw tooLarge();
        }
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Image is not valid base64");
        }
        if (bytes.length > maxUploadBytes) {
            throw tooLarge();
        }

        BufferedImage source = decode(bytes);
        BufferedImage flat = flatten(source);

        // Fall back to the original each time (no double-compression
        // artifacts); the size ladder restarts from the full image.
        int longest = Math.min(Math.max(flat.getWidth(), flat.getHeight()), maxDimension);
        while (true) {
            BufferedImage scaled = scaledToLongest(flat, longest);
            byte[] encoded = encodeWithin(scaled);
            if (encoded != null) {
                return Base64.getEncoder().encodeToString(encoded);
            }
            if (longest <= MIN_DIMENSION) {
                break;
            }
            longest = Math.max(MIN_DIMENSION, longest / 2);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Image cannot be compressed small enough to store; "
                        + "please upload a smaller or simpler image");
    }

    private String stripDataUriPrefix(String base64Image) {
        if (base64Image == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Visual measurement requires an 'image'");
        }
        String raw = base64Image.trim();
        if (raw.startsWith("data:")) {
            String marker = ";base64,";
            int index = raw.indexOf(marker);
            if (index < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Only base64 data URIs are supported");
            }
            raw = raw.substring(index + marker.length());
        }
        return raw;
    }

    private BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Image is not a decodable picture (JPEG/PNG/BMP)");
            }
            return image;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Image could not be read");
        }
    }

    /**
     * Draws the image onto an opaque RGB canvas, so PNG transparency
     * flattens to white instead of turning black in the JPEG.
     */
    private BufferedImage flatten(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return rgb;
    }

    /**
     * A copy scaled so the longest side is {@code longest}, aspect ratio
     * preserved and never upscaled.
     */
    private BufferedImage scaledToLongest(BufferedImage source, int longest) {
        int currentLongest = Math.max(source.getWidth(), source.getHeight());
        if (currentLongest <= longest) {
            return source;
        }
        double scale = (double) longest / currentLongest;
        try {
            return Thumbnails.of(source)
                    .scale(scale)
                    .imageType(BufferedImage.TYPE_INT_RGB)
                    .asBufferedImage();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Image could not be scaled");
        }
    }

    /**
     * Encodes as JPEG, lowering the quality from the configured start to
     * the floor until the result fits {@code maxStoredBytes}; null when
     * the floor is reached without fitting.
     */
    private byte[] encodeWithin(BufferedImage image) {
        double q = quality;
        while (true) {
            byte[] bytes = encode(image, q);
            if (bytes.length <= maxStoredBytes) {
                return bytes;
            }
            if (q <= qualityFloor) {
                return null;
            }
            q = Math.max(qualityFloor, q - QUALITY_STEP);
        }
    }

    private byte[] encode(BufferedImage image, double quality) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(image)
                    .size(image.getWidth(), image.getHeight())
                    .imageType(BufferedImage.TYPE_INT_RGB)
                    .outputFormat("jpeg")
                    .outputQuality(quality)
                    .toOutputStream(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Image could not be encoded");
        }
    }

    private ResponseStatusException tooLarge() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Image is larger than the " + maxUploadBytes / (1024 * 1024) + " MB upload limit");
    }
}
