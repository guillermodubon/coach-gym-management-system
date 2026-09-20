package io.github.guillermodubon.coachgym.user.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Validates staff photo signatures and bounds without decoding the complete
 * image into a raster. WebP is signature-validated; dimensions are inspected
 * when the installed JDK exposes a reader for the format.
 */
public final class StaffProfilePhotoInspector {

    public static final int MAX_DIMENSION = 4096;
    public static final long MAX_PIXEL_COUNT = 16_777_216L;

    private StaffProfilePhotoInspector() {
    }

    public static void requireSafe(StaffProfilePhotoContent content) {
        if (content == null) {
            throw new StaffProfileValidationException(
                    "Staff profile photo content is required.");
        }
        byte[] bytes = content.bytes();
        requireSignature(content.contentType(), bytes);
        requireDimensions(content.contentType(), bytes);
    }

    private static void requireSignature(String contentType, byte[] bytes) {
        boolean matches = switch (contentType) {
            case "image/jpeg" -> startsWith(bytes, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(bytes,
                    0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/webp" -> bytes.length >= 12
                    && startsWith(bytes, 0x52, 0x49, 0x46, 0x46)
                    && at(bytes, 8, 0x57, 0x45, 0x42, 0x50);
            default -> false;
        };
        if (!matches) {
            throw new StaffProfileValidationException(
                    "Staff profile photo signature is invalid.");
        }
    }

    private static void requireDimensions(String contentType, byte[] bytes) {
        if (contentType.equals("image/webp")) {
            return;
        }
        Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(contentType);
        if (!readers.hasNext()) {
            throw new StaffProfileValidationException(
                    "Staff profile photo format is not supported by the image inspector.");
        }
        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new StaffProfileValidationException(
                        "Staff profile photo could not be inspected.");
            }
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width < 1 || height < 1
                    || width > MAX_DIMENSION || height > MAX_DIMENSION
                    || ((long) width * height) > MAX_PIXEL_COUNT) {
                throw new StaffProfileValidationException(
                        "Staff profile photo dimensions exceed the allowed bounds.");
            }
        } catch (IOException | IndexOutOfBoundsException exception) {
            throw new StaffProfileValidationException(
                    "Staff profile photo could not be inspected.", exception);
        } finally {
            reader.dispose();
        }
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        return at(bytes, 0, signature);
    }

    private static boolean at(byte[] bytes, int offset, int... signature) {
        if (bytes == null || bytes.length < offset + signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if ((bytes[offset + index] & 0xFF) != signature[index]) {
                return false;
            }
        }
        return true;
    }
}
