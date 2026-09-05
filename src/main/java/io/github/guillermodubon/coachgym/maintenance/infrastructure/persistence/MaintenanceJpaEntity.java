package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceDetails;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceType;
import io.github.guillermodubon.coachgym.maintenance.application.MaintenanceStateConflictException;
import io.github.guillermodubon.coachgym.maintenance.application.MaintenanceVersionConflictException;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceCancellation;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceCompletion;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceDefinition;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceStatusTransition;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceUpdateDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "maintenances", schema = "gym")
class MaintenanceJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "maintenance_number", insertable = false, updatable = false)
    private Long maintenanceNumber;

    @Column(name = "maintenance_code", insertable = false, updatable = false)
    private String maintenanceCode;

    @Column(name = "equipment_id", nullable = false, updatable = false)
    private UUID equipmentId;

    @Column(name = "incident_id", updatable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_type", nullable = false, updatable = false)
    private MaintenanceType maintenanceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MaintenanceStatus status;

    @Column(name = "scheduled_on", nullable = false)
    private LocalDate scheduledOn;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "provider_name", length = 160)
    private String providerName;

    @Column(name = "technician_name", length = 160)
    private String technicianName;

    @Column(name = "estimated_cost", precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "actual_cost", precision = 12, scale = 2)
    private BigDecimal actualCost;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "currency",
            nullable = false,
            length = 3,
            columnDefinition = "char(3)")
    private String currency;

    @Column(
            name = "actions_taken",
            columnDefinition = "text")
    private String actionsTaken;

    @Column(
            name = "notes",
            columnDefinition = "text")
    private String notes;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "assigned_to_user_id")
    private UUID assignedToUserId;

    @Column(name = "completed_by_user_id")
    private UUID completedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected MaintenanceJpaEntity() {
    }

    static MaintenanceJpaEntity schedule(
            MaintenanceDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt) {
        Objects.requireNonNull(definition, "Maintenance definition is required.");
        Objects.requireNonNull(actor, "Authenticated actor is required.");
        Objects.requireNonNull(occurredAt, "Occurrence timestamp is required.");

        MaintenanceJpaEntity entity = new MaintenanceJpaEntity();
        entity.id = UUID.randomUUID();
        entity.equipmentId = definition.equipmentId();
        entity.incidentId = definition.incidentId();
        entity.maintenanceType = definition.maintenanceType();
        entity.status = MaintenanceStatus.SCHEDULED;
        entity.scheduledOn = definition.scheduledOn();
        entity.providerName = definition.providerName();
        entity.technicianName = definition.technicianName();
        entity.estimatedCost = definition.estimatedCost();
        entity.currency = definition.currency();
        entity.notes = definition.notes();
        entity.createdByUserId = actor.id();
        entity.assignedToUserId = definition.assignedToUserId();
        entity.createdAt = occurredAt;
        entity.updatedAt = occurredAt;
        entity.version = 0L;
        return entity;
    }

    void updateScheduled(
            long expectedVersion,
            MaintenanceUpdateDefinition definition,
            Instant occurredAt) {
        requireVersion(expectedVersion);
        requireStatus(MaintenanceStatus.SCHEDULED,
                "Only scheduled maintenance can be updated.");
        scheduledOn = definition.scheduledOn();
        providerName = definition.providerName();
        technicianName = definition.technicianName();
        estimatedCost = definition.estimatedCost();
        currency = definition.currency();
        notes = definition.notes();
        assignedToUserId = definition.assignedToUserId();
        updatedAt = occurredAt;
    }

    void transition(
            long expectedVersion,
            MaintenanceStatusTransition transition,
            Instant startedAt,
            Instant occurredAt) {
        requireVersion(expectedVersion);
        requireCurrentStatus(transition.previousStatus());
        status = transition.resultingStatus();
        if (status == MaintenanceStatus.IN_PROGRESS) {
            this.startedAt = Objects.requireNonNull(
                    startedAt, "Start timestamp is required.");
        }
        updatedAt = occurredAt;
    }

    void complete(
            long expectedVersion,
            MaintenanceStatusTransition transition,
            MaintenanceCompletion completion,
            AuthenticatedActor actor,
            Instant occurredAt) {
        requireVersion(expectedVersion);
        requireCurrentStatus(transition.previousStatus());
        completion.validateAgainst(startedAt);
        completion.validateCurrency(currency);
        status = transition.resultingStatus();
        completedAt = completion.completedAt();
        actionsTaken = completion.actionsTaken();
        actualCost = completion.actualCost();
        completedByUserId = actor.id();
        updatedAt = occurredAt;
    }

    void cancel(
            long expectedVersion,
            MaintenanceStatus currentStatus,
            MaintenanceStatusTransition transition,
            MaintenanceCancellation cancellation,
            Instant occurredAt) {
        requireVersion(expectedVersion);
        requireCurrentStatus(currentStatus);
        requireCurrentStatus(transition.previousStatus());
        cancellation.validateFor(currentStatus);
        status = transition.resultingStatus();
        updatedAt = occurredAt;
    }

    MaintenanceDetails toDetails(
            String equipmentCode,
            String equipmentName,
            String incidentCode) {
        return new MaintenanceDetails(
                id,
                maintenanceNumber == null ? 0L : maintenanceNumber,
                maintenanceCode,
                equipmentId,
                equipmentCode,
                equipmentName,
                incidentId,
                incidentCode,
                maintenanceType,
                status,
                scheduledOn,
                startedAt,
                completedAt,
                providerName,
                technicianName,
                estimatedCost,
                actualCost,
                currency,
                actionsTaken,
                notes,
                createdByUserId,
                assignedToUserId,
                completedByUserId,
                createdAt,
                updatedAt,
                version);
    }

    private void requireVersion(long expectedVersion) {
        if (version != expectedVersion) {
            throw new MaintenanceVersionConflictException(id, expectedVersion);
        }
    }

    private void requireStatus(MaintenanceStatus required, String message) {
        if (status != required) {
            throw new MaintenanceStateConflictException(id, message);
        }
    }

    private void requireCurrentStatus(MaintenanceStatus expected) {
        if (status != expected) {
            throw new MaintenanceStateConflictException(
                    id,
                    "Maintenance status is " + status
                            + ", expected " + expected + ".");
        }
    }

    UUID id() { return id; }
    UUID equipmentId() { return equipmentId; }
    UUID incidentId() { return incidentId; }
    MaintenanceStatus status() { return status; }
    long version() { return version; }
    Instant startedAt() { return startedAt; }
    Instant completedAt() { return completedAt; }
    String currency() { return currency; }
}
