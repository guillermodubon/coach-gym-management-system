package io.github.guillermodubon.coachgym.accesscredential;

import java.util.Locale;

/** Immutable PNG artifact exchanged through the future renderer and storage ports. */
public record AccessCredentialDocument(
        String contentType,
        byte[] bytes) {

    /** Defensive upper bound for a printable QR artifact. */
    public static final long MAX_SIZE_BYTES = 1024L * 1024L;

    public AccessCredentialDocument {
        contentType = normalizeContentType(contentType);
        if (!"image/png".equals(contentType)) {
            throw new IllegalArgumentException(
                    "Access credential document content type must be image/png.");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException(
                    "Access credential document content is required.");
        }
        if (bytes.length > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Access credential document exceeds the maximum allowed size.");
        }
        bytes = bytes.clone();
    }

    /** Returns the document size without exposing the backing array. */
    public long sizeBytes() {
        return bytes.length;
    }

    /** Returns a defensive copy of the PNG bytes. */
    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    private static String normalizeContentType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Access credential document content type is required.");
        }
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
