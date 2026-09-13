package io.github.guillermodubon.coachgym.accesscredential.infrastructure.persistence;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialPersistenceCommand;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStoredDocument;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "gym", name = "access_credentials")
class AccessCredentialJpaEntity {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "credential_code", nullable = false, length = 64, updatable = false)
    private String credentialCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_fingerprint", nullable = false, length = 64, updatable = false)
    private String tokenFingerprint;

    @Column(name = "token_scheme_version", nullable = false, length = 32, updatable = false)
    private String tokenSchemeVersion;

    @Column(name = "payload_version", nullable = false, length = 16, updatable = false)
    private String payloadVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessCredentialStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "issued_by_user_id", nullable = false, updatable = false)
    private UUID issuedByUserId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by_user_id")
    private UUID revokedByUserId;

    @Column(name = "revocation_reason", length = 2000)
    private String revocationReason;

    @Column(name = "replaced_by_credential_id")
    private UUID replacedByCredentialId;

    @Column(name = "storage_key", nullable = false, length = 500, updatable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 50, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "checksum_sha256", nullable = false, length = 64, updatable = false)
    private String checksumSha256;

    @Column(name = "renderer_version", length = 64, updatable = false)
    private String rendererVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AccessCredentialJpaEntity() {
    }

    static AccessCredentialJpaEntity create(AccessCredentialPersistenceCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Credential persistence command is required.");
        }
        AccessCredentialJpaEntity entity = new AccessCredentialJpaEntity();
        entity.id = command.id();
        entity.clientId = command.clientId();
        entity.credentialCode = command.credentialCode();
        entity.tokenFingerprint = command.tokenFingerprint();
        entity.tokenSchemeVersion = command.tokenSchemeVersion();
        entity.payloadVersion = command.payloadVersion();
        entity.status = AccessCredentialStatus.ACTIVE;
        entity.issuedAt = command.issuedAt();
        entity.issuedByUserId = command.issuedByUserId();
        entity.storageKey = command.storageKey();
        entity.contentType = command.contentType();
        entity.sizeBytes = command.sizeBytes();
        entity.checksumSha256 = command.checksumSha256();
        entity.rendererVersion = command.rendererVersion();
        entity.createdAt = command.issuedAt();
        entity.updatedAt = command.issuedAt();
        return entity;
    }

    void revoke(String reason, UUID actorId, Instant revokedAt) {
        if (status != AccessCredentialStatus.ACTIVE) {
            throw new IllegalStateException("Only an active credential can be revoked.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Credential revocation reason is required.");
        }
        if (actorId == null || revokedAt == null) {
            throw new IllegalArgumentException("Credential revocation metadata is required.");
        }
        if (revokedAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("Credential revocation cannot precede issuance.");
        }
        status = AccessCredentialStatus.REVOKED;
        this.revokedAt = revokedAt;
        revokedByUserId = actorId;
        revocationReason = reason.strip();
    }

    void attachReplacement(UUID replacementCredentialId) {
        if (status != AccessCredentialStatus.REVOKED
                || this.replacedByCredentialId != null) {
            throw new IllegalStateException("Credential replacement link is not available.");
        }
        if (replacementCredentialId == null || id.equals(replacementCredentialId)) {
            throw new IllegalArgumentException("A different replacement credential is required.");
        }
        this.replacedByCredentialId = replacementCredentialId;
    }

    AccessCredentialDetails toDetails() {
        return new AccessCredentialDetails(
                id,
                clientId,
                credentialCode,
                status,
                payloadVersion,
                issuedAt,
                issuedByUserId,
                revokedAt,
                revokedByUserId,
                replacedByCredentialId,
                version);
    }

    AccessCredentialStoredDocument toStoredDocument() {
        return new AccessCredentialStoredDocument(
                storageKey,
                contentType,
                sizeBytes,
                checksumSha256,
                rendererVersion);
    }

    UUID id() {
        return id;
    }

    UUID clientId() {
        return clientId;
    }

    AccessCredentialStatus status() {
        return status;
    }

    UUID replacedByCredentialId() {
        return replacedByCredentialId;
    }

    long version() {
        return version;
    }

    String tokenFingerprint() {
        return tokenFingerprint;
    }
}
