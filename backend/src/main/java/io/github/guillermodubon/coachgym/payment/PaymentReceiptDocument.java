package io.github.guillermodubon.coachgym.payment;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Immutable PDF document returned by a receipt renderer or storage boundary. */
public record PaymentReceiptDocument(
        String contentType,
        byte[] bytes,
        String checksumSha256) {

    public static final long MAX_SIZE_BYTES = 10L * 1024L * 1024L;

    public PaymentReceiptDocument {
        contentType = normalizeContentType(contentType);
        if (!"application/pdf".equals(contentType)) {
            throw new IllegalArgumentException("Receipt document content type must be application/pdf.");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Receipt document content is required.");
        }
        if (bytes.length > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Receipt document exceeds the maximum allowed size.");
        }
        bytes = bytes.clone();
        checksumSha256 = normalizeChecksum(checksumSha256);
        if (!checksumSha256.equals(checksum(bytes))) {
            throw new IllegalArgumentException("Receipt document checksum does not match its content.");
        }
    }

    public static PaymentReceiptDocument fromPdfBytes(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Receipt document content is required.");
        }
        return new PaymentReceiptDocument(
                "application/pdf", bytes, checksum(bytes));
    }

    public long sizeBytes() {
        return bytes.length;
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    private static String normalizeContentType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Receipt document content type is required.");
        }
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeChecksum(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Receipt document checksum must be a SHA-256 hexadecimal value.");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String checksum(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
