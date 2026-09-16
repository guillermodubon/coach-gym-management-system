package io.github.guillermodubon.coachgym.notification;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValuePolicy;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Immutable canonical attachment exchanged with the provider-neutral sender. */
public record EmailAttachment(
        String filename,
        String contentType,
        byte[] bytes,
        String checksumSha256) {

    public EmailAttachment(String filename, String contentType, byte[] bytes) {
        this(filename, contentType, bytes, checksum(bytes));
    }

    public EmailAttachment {
        filename = EmailDeliveryValuePolicy.normalizeFilename(filename);
        contentType = normalizeContentType(contentType);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Email attachment content is required.");
        }
        if (bytes.length > EmailDeliveryValuePolicy.MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException("Email attachment exceeds the maximum allowed size.");
        }
        bytes = bytes.clone();
        checksumSha256 = EmailDeliveryValuePolicy.normalizeChecksum(
                checksumSha256, "Email attachment checksum");
        if (!checksumSha256.equals(checksum(bytes))) {
            throw new IllegalArgumentException("Email attachment checksum does not match its content.");
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
        return "EmailAttachment[filename=" + filename
                + ", contentType=" + contentType
                + ", sizeBytes=" + bytes.length
                + ", checksumPresent=true]";
    }

    private static String normalizeContentType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email attachment content type is required.");
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (!normalized.equals("application/pdf") && !normalized.equals("image/png")) {
            throw new IllegalArgumentException("Unsupported email attachment content type.");
        }
        return normalized;
    }

    private static String checksum(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("Email attachment content is required.");
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
