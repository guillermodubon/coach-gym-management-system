package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Append-only JPA representation of {@code gym.incident_status_history}. */
@Entity
@Table(name = "incident_status_history", schema = "gym")
class IncidentStatusHistoryJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "incident_id", nullable = false, updatable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20, updatable = false)
    private IncidentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20, updatable = false)
    private IncidentStatus newStatus;

    @Column(name = "reason", columnDefinition = "text", updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "changed_by_user_id", updatable = false)
    private UUID changedByUserId;

    protected IncidentStatusHistoryJpaEntity() {
    }

    private IncidentStatusHistoryJpaEntity(
            UUID id,
            UUID incidentId,
            IncidentStatus previousStatus,
            IncidentStatus newStatus,
            String reason,
            Instant occurredAt,
            UUID changedByUserId) {
        this.id = Objects.requireNonNull(id, "History id is required.");
        this.incidentId = Objects.requireNonNull(incidentId, "Incident id is required.");
        this.previousStatus = previousStatus;
        this.newStatus = Objects.requireNonNull(newStatus, "New incident status is required.");
        this.reason = requireText(reason);
        this.occurredAt = Objects.requireNonNull(occurredAt, "Occurrence timestamp is required.");
        this.changedByUserId = Objects.requireNonNull(changedByUserId, "History actor is required.");
        if (previousStatus == newStatus) {
            throw new IllegalArgumentException("Incident history must represent a state change.");
        }
        if (previousStatus == null && newStatus != IncidentStatus.OPEN) {
            throw new IllegalArgumentException("Initial incident history must create OPEN.");
        }
    }

    static IncidentStatusHistoryJpaEntity initial(
            UUID incidentId,
            UUID actorId,
            Instant occurredAt) {
        return new IncidentStatusHistoryJpaEntity(
                UUID.randomUUID(), incidentId, null, IncidentStatus.OPEN,
                "Incident reported.", occurredAt, actorId);
    }

    static IncidentStatusHistoryJpaEntity transition(
            UUID incidentId,
            IncidentStatus previousStatus,
            IncidentStatus newStatus,
            String reason,
            UUID actorId,
            Instant occurredAt) {
        return new IncidentStatusHistoryJpaEntity(
                UUID.randomUUID(), incidentId, previousStatus, newStatus,
                reason, occurredAt, actorId);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Incident history reason is required.");
        }
        return value.trim();
    }

    UUID id() { return id; }
    UUID incidentId() { return incidentId; }
    IncidentStatus previousStatus() { return previousStatus; }
    IncidentStatus newStatus() { return newStatus; }
    String reason() { return reason; }
    Instant occurredAt() { return occurredAt; }
    UUID changedByUserId() { return changedByUserId; }
}
