package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatusHistoryDetails;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceStatusTransition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "maintenance_status_history", schema = "gym")
class MaintenanceStatusHistoryJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "maintenance_id", nullable = false, updatable = false)
    private UUID maintenanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", updatable = false)
    private MaintenanceStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, updatable = false)
    private MaintenanceStatus newStatus;

    @Column(
            name = "reason",
            updatable = false,
            columnDefinition = "text")
    private String reason;

    @Column(name = "changed_by_user_id", updatable = false)
    private UUID changedByUserId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected MaintenanceStatusHistoryJpaEntity() {
    }

    static MaintenanceStatusHistoryJpaEntity initial(
            UUID maintenanceId,
            AuthenticatedActor actor,
            Instant occurredAt) {
        return create(
                maintenanceId,
                null,
                MaintenanceStatus.SCHEDULED,
                "Maintenance scheduled.",
                actor,
                occurredAt);
    }

    static MaintenanceStatusHistoryJpaEntity transition(
            UUID maintenanceId,
            MaintenanceStatusTransition transition,
            AuthenticatedActor actor,
            Instant occurredAt) {
        Objects.requireNonNull(transition, "Maintenance transition is required.");
        return create(
                maintenanceId,
                transition.previousStatus(),
                transition.resultingStatus(),
                transition.reason(),
                actor,
                occurredAt);
    }

    private static MaintenanceStatusHistoryJpaEntity create(
            UUID maintenanceId,
            MaintenanceStatus previousStatus,
            MaintenanceStatus newStatus,
            String reason,
            AuthenticatedActor actor,
            Instant occurredAt) {
        MaintenanceStatusHistoryJpaEntity entity =
                new MaintenanceStatusHistoryJpaEntity();
        entity.id = UUID.randomUUID();
        entity.maintenanceId = Objects.requireNonNull(
                maintenanceId, "Maintenance id is required.");
        entity.previousStatus = previousStatus;
        entity.newStatus = Objects.requireNonNull(
                newStatus, "New status is required.");
        entity.reason = Objects.requireNonNull(reason, "Reason is required.");
        entity.changedByUserId = Objects.requireNonNull(
                actor, "Authenticated actor is required.").id();
        entity.occurredAt = Objects.requireNonNull(
                occurredAt, "Occurrence timestamp is required.");
        return entity;
    }

    MaintenanceStatusHistoryDetails toDetails() {
        return new MaintenanceStatusHistoryDetails(
                id,
                maintenanceId,
                previousStatus,
                newStatus,
                reason,
                occurredAt,
                changedByUserId);
    }
}
