package io.github.guillermodubon.coachgym.accesscredential;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Authoritative active credential projection and its canonical PNG artifact.
 * Raw token material and storage paths are intentionally absent.
 */
public record AccessCredentialEmailSource(
        AccessCredentialDetails credential,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        String rendererVersion,
        AccessCredentialDocument document) {

    public AccessCredentialEmailSource {
        credential = Objects.requireNonNull(credential, "Credential details are required.");
        if (credential.status() != AccessCredentialStatus.ACTIVE) {
            throw new IllegalArgumentException("Only an active credential can be delivered.");
        }
        contentType = requiredContentType(contentType);
        if (sizeBytes < 1 || sizeBytes > AccessCredentialDocument.MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Credential document size is invalid.");
        }
        checksumSha256 = normalizeChecksum(checksumSha256);
        rendererVersion = normalizeRendererVersion(rendererVersion);
        document = Objects.requireNonNull(document, "Credential document is required.");
        if (!document.contentType().equals(contentType)
                || document.sizeBytes() != sizeBytes
                || !checksumSha256.equals(checksum(document.bytes()))) {
            throw new IllegalArgumentException(
                    "Credential document does not match its persisted metadata.");
        }
    }

    public java.util.UUID credentialId() {
        return credential.id();
    }

    public java.util.UUID clientId() {
        return credential.clientId();
    }

    private static String requiredContentType(String value) {
        if (value == null || !"image/png".equals(value.strip().toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Credential document content type must be image/png.");
        }
        return "image/png";
    }

    private static String normalizeChecksum(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Credential document checksum is invalid.");
        }
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalizeRendererVersion(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("Credential renderer version is too long.");
        }
        return normalized;
    }

    private static String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
