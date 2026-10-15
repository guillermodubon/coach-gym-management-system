package io.github.guillermodubon.coachgym.accesscredential.application;

import java.util.Locale;

/**
 * Nonsecret metadata returned after a canonical credential PNG is stored.
 * Absolute paths and document bytes intentionally do not cross this boundary.
 */
public record AccessCredentialStoredDocument(
        String storageKey,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        String rendererVersion) {

    private static final int MAX_RENDERER_VERSION_LENGTH = 64;
    private static final long MAX_SIZE_BYTES = 1024L * 1024L;

    public AccessCredentialStoredDocument {
        storageKey = AccessCredentialStorageKey.requireCanonical(storageKey);
        contentType = requiredContentType(contentType);
        if (sizeBytes < 1 || sizeBytes > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Access credential document size must be between 1 and 1048576 bytes.");
        }
        if (checksumSha256 == null || !checksumSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Access credential document checksum is invalid.");
        }
        rendererVersion = optionalRendererVersion(rendererVersion);
    }

    @Override
    public String toString() {
        return "AccessCredentialStoredDocument["
                + "storageKeyPresent=true"
                + ", contentType=" + contentType
                + ", sizeBytes=" + sizeBytes
                + ", checksumPresent=true"
                + ", rendererVersion=" + rendererVersion
                + ']';
    }

    private static String requiredContentType(String value) {
        if (value == null || value.isBlank()
                || !"image/png".equals(value.strip().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Access credential document content type must be image/png.");
        }
        return "image/png";
    }

    private static String optionalRendererVersion(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_RENDERER_VERSION_LENGTH) {
            throw new IllegalArgumentException(
                    "Access credential renderer version must not exceed 64 characters.");
        }
        return normalized;
    }
}
