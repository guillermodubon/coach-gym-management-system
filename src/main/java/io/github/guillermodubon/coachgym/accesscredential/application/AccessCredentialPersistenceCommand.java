package io.github.guillermodubon.coachgym.accesscredential.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal persistence input for a newly issued credential.
 *
 * <p>It contains only protected token metadata. The raw token is deliberately
 * not a member of this command.</p>
 */
public final class AccessCredentialPersistenceCommand {

    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_SCHEME_LENGTH = 32;
    private static final int MAX_PAYLOAD_VERSION_LENGTH = 16;
    private static final int MAX_STORAGE_KEY_LENGTH = 500;
    private static final int MAX_CONTENT_TYPE_LENGTH = 50;
    private static final int MAX_RENDERER_VERSION_LENGTH = 64;
    private static final long MAX_ARTIFACT_SIZE = 1024L * 1024L;

    private final UUID id;
    private final UUID clientId;
    private final String credentialCode;
    private final String tokenFingerprint;
    private final String tokenSchemeVersion;
    private final String payloadVersion;
    private final Instant issuedAt;
    private final UUID issuedByUserId;
    private final String storageKey;
    private final String contentType;
    private final long sizeBytes;
    private final String checksumSha256;
    private final String rendererVersion;

    public AccessCredentialPersistenceCommand(
            UUID id,
            UUID clientId,
            String credentialCode,
            String tokenFingerprint,
            String tokenSchemeVersion,
            String payloadVersion,
            Instant issuedAt,
            UUID issuedByUserId,
            String storageKey,
            String contentType,
            long sizeBytes,
            String checksumSha256,
            String rendererVersion) {

        this.id = requiredId(id, "Credential id");
        this.clientId = requiredId(clientId, "Client id");
        this.credentialCode = requiredText(
                credentialCode, "Credential code", MAX_CODE_LENGTH);
        this.tokenFingerprint = requiredFingerprint(tokenFingerprint);
        this.tokenSchemeVersion = requiredPattern(
                tokenSchemeVersion,
                "Token scheme version",
                "[a-z0-9-]{1," + MAX_SCHEME_LENGTH + "}",
                MAX_SCHEME_LENGTH);
        this.payloadVersion = requiredPattern(
                payloadVersion,
                "Payload version",
                "v[0-9]{1," + (MAX_PAYLOAD_VERSION_LENGTH - 1) + "}",
                MAX_PAYLOAD_VERSION_LENGTH);
        this.issuedAt = requiredTimestamp(issuedAt, "Credential issued timestamp");
        this.issuedByUserId = requiredId(issuedByUserId, "Credential issuer id");
        this.storageKey = requiredText(
                storageKey, "Credential storage key", MAX_STORAGE_KEY_LENGTH);
        this.contentType = requiredContentType(contentType);
        if (sizeBytes <= 0 || sizeBytes > MAX_ARTIFACT_SIZE) {
            throw new IllegalArgumentException(
                    "Credential artifact size must be between 1 and 1048576 bytes.");
        }
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = requiredFingerprint(checksumSha256);
        this.rendererVersion = optionalText(
                rendererVersion, "Credential renderer version", MAX_RENDERER_VERSION_LENGTH);
    }

    public UUID id() {
        return id;
    }

    public UUID clientId() {
        return clientId;
    }

    public String credentialCode() {
        return credentialCode;
    }

    public String tokenFingerprint() {
        return tokenFingerprint;
    }

    public String tokenSchemeVersion() {
        return tokenSchemeVersion;
    }

    public String payloadVersion() {
        return payloadVersion;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public UUID issuedByUserId() {
        return issuedByUserId;
    }

    public String storageKey() {
        return storageKey;
    }

    public String contentType() {
        return contentType;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String checksumSha256() {
        return checksumSha256;
    }

    public String rendererVersion() {
        return rendererVersion;
    }

    @Override
    public String toString() {
        return "AccessCredentialPersistenceCommand["
                + "id=" + id
                + ", clientId=" + clientId
                + ", credentialCode=" + credentialCode
                + ", tokenFingerprintPresent=true"
                + ", tokenSchemeVersion=" + tokenSchemeVersion
                + ", payloadVersion=" + payloadVersion
                + ", issuedAt=" + issuedAt
                + ", issuedByUserId=" + issuedByUserId
                + ", storageKeyPresent=true"
                + ", contentType=" + contentType
                + ", sizeBytes=" + sizeBytes
                + ", checksumPresent=true"
                + ", rendererVersion=" + rendererVersion
                + ']';
    }

    static String requiredFingerprint(String value) {
        return requiredPattern(value, "Credential fingerprint", "[0-9a-f]{64}", 64);
    }

    private static UUID requiredId(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value;
    }

    private static Instant requiredTimestamp(Instant value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value;
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + maxLength + " characters.");
        }
        return normalized;
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requiredText(value, field, maxLength);
    }

    private static String requiredPattern(
            String value,
            String field,
            String pattern,
            int maxLength) {
        String normalized = requiredText(value, field, maxLength);
        if (!normalized.matches(pattern)) {
            throw new IllegalArgumentException(field + " has an invalid format.");
        }
        return normalized;
    }

    private static String requiredContentType(String value) {
        String normalized = requiredText(value, "Credential content type", MAX_CONTENT_TYPE_LENGTH);
        if (!"image/png".equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Credential content type must be image/png.");
        }
        return "image/png";
    }
}
