package io.github.guillermodubon.coachgym.accesscredential.infrastructure.persistence;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialHistoryDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialHistoryPersistenceCommand;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "gym", name = "access_credential_history")
class AccessCredentialHistoryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "credential_id", nullable = false, updatable = false)
    private UUID credentialId;

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20, updatable = false)
    private AccessCredentialStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20, updatable = false)
    private AccessCredentialStatus newStatus;

    @Column(name = "reason", length = 2000, updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "changed_by_user_id", nullable = false, updatable = false)
    private UUID changedByUserId;

    @Column(name = "replacement_credential_id", updatable = false)
    private UUID replacementCredentialId;

    protected AccessCredentialHistoryJpaEntity() {
    }

    static AccessCredentialHistoryJpaEntity create(
            AccessCredentialHistoryPersistenceCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Credential history command is required.");
        }
        AccessCredentialHistoryDetails details = new AccessCredentialHistoryDetails(
                UUID.randomUUID(),
                command.credentialId(),
                command.clientId(),
                command.previousStatus(),
                command.newStatus(),
                command.reason(),
                command.occurredAt(),
                command.changedByUserId(),
                command.replacementCredentialId());

        AccessCredentialHistoryJpaEntity entity = new AccessCredentialHistoryJpaEntity();
        entity.id = details.id();
        entity.credentialId = details.credentialId();
        entity.clientId = details.clientId();
        entity.previousStatus = details.previousStatus();
        entity.newStatus = details.newStatus();
        entity.reason = details.reason();
        entity.occurredAt = details.occurredAt();
        entity.changedByUserId = details.changedByUserId();
        entity.replacementCredentialId = details.replacementCredentialId();
        return entity;
    }

    AccessCredentialHistoryDetails toDetails() {
        return new AccessCredentialHistoryDetails(
                id,
                credentialId,
                clientId,
                previousStatus,
                newStatus,
                reason,
                occurredAt,
                changedByUserId,
                replacementCredentialId);
    }
}
