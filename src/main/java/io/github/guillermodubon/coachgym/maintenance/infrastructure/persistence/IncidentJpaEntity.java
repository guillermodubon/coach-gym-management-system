package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentDefinition;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentStatusTransition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** JPA representation of {@code gym.incidents}. */
@Entity
@Table(name = "incidents", schema = "gym")
class IncidentJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "incident_number", insertable = false, updatable = false)
    private Long incidentNumber;

    @Column(name = "incident_code", insertable = false, updatable = false, length = 32)
    private String incidentCode;

    @Column(name = "equipment_id", nullable = false, updatable = false)
    private UUID equipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IncidentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private IncidentPriority priority;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private Instant reportedAt;

    @Column(name = "reported_by_user_id", nullable = false, updatable = false)
    private UUID reportedByUserId;

    @Column(name = "assigned_to_user_id")
    private UUID assignedToUserId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by_user_id")
    private UUID resolvedByUserId;

    @Column(name = "resolution_notes", columnDefinition = "text")
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected IncidentJpaEntity() {
    }

    private IncidentJpaEntity(
            UUID id,
            IncidentDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt) {
        this.id = Objects.requireNonNull(id, "Incident id is required.");
        this.equipmentId = definition.equipmentId();
        this.status = IncidentStatus.OPEN;
        this.priority = definition.priority();
        this.description = definition.description();
        this.reportedAt = occurredAt;
        this.reportedByUserId = actor.id();
        this.createdAt = occurredAt;
        this.updatedAt = occurredAt;
    }

    static IncidentJpaEntity report(
            UUID id,
            IncidentDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt) {
        Objects.requireNonNull(definition, "Incident definition is required.");
        Objects.requireNonNull(actor, "Authenticated actor is required.");
        Objects.requireNonNull(actor.id(), "Authenticated actor id is required.");
        Objects.requireNonNull(occurredAt, "Occurrence timestamp is required.");
        return new IncidentJpaEntity(id, definition, actor, occurredAt);
    }

    void applyTransition(
            IncidentStatusTransition transition,
            String resolvedNotes,
            AuthenticatedActor actor,
            Instant occurredAt) {
        Objects.requireNonNull(transition, "Incident transition is required.");
        Objects.requireNonNull(actor, "Authenticated actor is required.");
        Objects.requireNonNull(actor.id(), "Authenticated actor id is required.");
        Objects.requireNonNull(occurredAt, "Occurrence timestamp is required.");
        if (status != transition.previousStatus()) {
            throw new IllegalStateException("Incident status changed before persistence.");
        }
        status = transition.resultingStatus();
        updatedAt = occurredAt;
        if (status == IncidentStatus.RESOLVED) {
            if (resolvedNotes == null || resolvedNotes.isBlank()) {
                throw new IllegalArgumentException("Resolution notes are required.");
            }
            resolvedAt = occurredAt;
            resolvedByUserId = actor.id();
            resolutionNotes = resolvedNotes.trim();
        }
    }

    void changePriority(
            IncidentPriority newPriority,
            Instant occurredAt) {
        Objects.requireNonNull(newPriority, "Incident priority is required.");
        Objects.requireNonNull(occurredAt, "Occurrence timestamp is required.");
        if (priority == newPriority) {
            throw new IllegalArgumentException("Incident priority must change.");
        }
        priority = newPriority;
        updatedAt = occurredAt;
    }

    UUID id() { return id; }
    Long incidentNumber() { return incidentNumber; }
    String incidentCode() { return incidentCode; }
    UUID equipmentId() { return equipmentId; }
    IncidentStatus status() { return status; }
    IncidentPriority priority() { return priority; }
    String description() { return description; }
    Instant reportedAt() { return reportedAt; }
    UUID reportedByUserId() { return reportedByUserId; }
    UUID assignedToUserId() { return assignedToUserId; }
    Instant resolvedAt() { return resolvedAt; }
    UUID resolvedByUserId() { return resolvedByUserId; }
    String resolutionNotes() { return resolutionNotes; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
    long version() { return version; }
}
