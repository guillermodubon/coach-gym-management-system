package io.github.guillermodubon.coachgym.user.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Technology-neutral bytes crossing the staff-photo storage port.
 * Signature inspection is intentionally deferred to the photo-security block;
 * this contract only protects bounded size, metadata, and checksum integrity.
 */
public record StaffProfilePhotoContent(
        String contentType,
        byte[] bytes,
        String checksumSha256) {

    public static final long MAX_SIZE_BYTES = 5L * 1024L * 1024L;

    /** Builds content from an upload while deriving, rather than trusting, its checksum. */
    public StaffProfilePhotoContent(String contentType, byte[] bytes) {
        this(contentType, bytes, checksum(bytes));
    }

    public StaffProfilePhotoContent {
        contentType = normalizeContentType(contentType);
        if (bytes == null || bytes.length == 0) {
            throw new StaffProfileValidationException(
                    "Staff profile photo content is required.");
        }
        if (bytes.length > MAX_SIZE_BYTES) {
            throw new StaffProfilePhotoTooLargeException();
        }
        bytes = bytes.clone();
        checksumSha256 = normalizeChecksum(checksumSha256);
        if (!checksumSha256.equals(checksum(bytes))) {
            throw new StaffProfileValidationException(
                    "Staff profile photo checksum does not match its content.");
        }
    }

    public long sizeBytes() {
        return bytes.length;
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public String toString() {
        return "StaffProfilePhotoContent[contentType=" + contentType
                + ", sizeBytes=" + sizeBytes()
                + ", checksumPresent=" + (checksumSha256 != null) + "]";
    }

    private static String normalizeContentType(String value) {
        if (value == null || value.isBlank()) {
            throw new StaffProfileValidationException(
                    "Staff profile photo content type is required.");
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (!java.util.Set.of("image/jpeg", "image/png", "image/webp")
                .contains(normalized)) {
            throw new StaffProfileValidationException(
                    "Unsupported staff profile photo content type.");
        }
        return normalized;
    }

    private static String normalizeChecksum(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new StaffProfileValidationException(
                    "Staff profile photo checksum must be a SHA-256 hexadecimal value.");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String checksum(byte[] content) {
        if (content == null) {
            throw new StaffProfileValidationException(
                    "Staff profile photo content is required.");
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
