package io.github.guillermodubon.coachgym.equipment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for {@code gym.equipment_status_history}.
 *
 * <p>This table is append-only; no update or delete paths exist.
 * {@code previousStatus} is nullable: a {@code null} value is valid for
 * the initial registration history entry where there was no prior state
 * (DB check constraint mirrors this).
 *
 * <p>{@code occurredAt} is set by the application from an injected
 * {@link java.time.Clock}; the DB default is a safety net only.
 */
@Entity
@Table(schema = "gym", name = "equipment_status_history")
class EquipmentStatusHistoryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "equipment_id", nullable = false, updatable = false)
    private UUID equipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20, updatable = false)
    private EquipmentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20, updatable = false)
    private EquipmentStatus newStatus;

    @Column(columnDefinition = "text", updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "changed_by_user_id", updatable = false)
    private UUID changedByUserId;

    protected EquipmentStatusHistoryJpaEntity() {
    }

    /**
     * Factory method for a catalog-managed status transition.
     *
     * @param id             application-generated UUID
     * @param equipmentId    the equipment this history row belongs to
     * @param previousStatus the status before the transition (may be null for initial row)
     * @param newStatus      the resulting status
     * @param reason         the normalized reason (non-blank for catalog transitions)
     * @param actor          the authenticated user who initiated the transition
     * @param occurredAt     the application-layer timestamp
     */
    static EquipmentStatusHistoryJpaEntity create(
            UUID id,
            UUID equipmentId,
            EquipmentStatus previousStatus,
            EquipmentStatus newStatus,
            String reason,
            AuthenticatedActor actor,
            Instant occurredAt) {
        EquipmentStatusHistoryJpaEntity entity = new EquipmentStatusHistoryJpaEntity();
        entity.id = id;
        entity.equipmentId = equipmentId;
        entity.previousStatus = previousStatus;
        entity.newStatus = newStatus;
        entity.reason = reason;
        entity.changedByUserId = actor != null ? actor.id() : null;
        entity.occurredAt = occurredAt;
        return entity;
    }

    UUID id() { return id; }
    UUID equipmentId() { return equipmentId; }
    EquipmentStatus previousStatus() { return previousStatus; }
    EquipmentStatus newStatus() { return newStatus; }
    String reason() { return reason; }
    Instant occurredAt() { return occurredAt; }
    UUID changedByUserId() { return changedByUserId; }
}
