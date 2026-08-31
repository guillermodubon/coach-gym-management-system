package io.github.guillermodubon.coachgym.equipment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity for {@code gym.equipment}.
 *
 * <p>Both {@code equipmentNumber} and {@code equipmentCode} are
 * {@code GENERATED ALWAYS} columns; they are declared with
 * {@code insertable = false, updatable = false} so the application never
 * attempts to write to them. After every INSERT or UPDATE the entity manager
 * is refreshed to obtain the DB-generated values.
 */
@Entity
@Table(schema = "gym", name = "equipment")
class EquipmentJpaEntity {

    @Id
    private UUID id;

    /** DB GENERATED ALWAYS AS IDENTITY — never written by the application. */
    @Column(name = "equipment_number",
            nullable = false, insertable = false, updatable = false)
    private Long equipmentNumber;

    /** DB stored generated column — never written by the application. */
    @Column(name = "equipment_code",
            nullable = false, insertable = false, updatable = false, length = 32)
    private String equipmentCode;

    @Column(name = "equipment_category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 120)
    private String manufacturer;

    @Column(length = 120)
    private String model;

    @Column(name = "serial_number", length = 120)
    private String serialNumber;

    @Column(length = 160)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EquipmentStatus status;

    @Column(name = "purchased_on")
    private LocalDate purchasedOn;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "retired_by_user_id")
    private UUID retiredByUserId;

    @Column(name = "retirement_reason", columnDefinition = "text")
    private String retirementReason;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected EquipmentJpaEntity() {
    }

    /**
     * Factory method for equipment registration.
     * Initial status is always {@code AVAILABLE}; retirement columns remain null.
     */
    static EquipmentJpaEntity register(
            UUID id,
            EquipmentDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt) {
        EquipmentJpaEntity entity = new EquipmentJpaEntity();
        entity.id = id;
        entity.status = EquipmentStatus.AVAILABLE;
        entity.createdByUserId = actor.id();
        entity.updatedByUserId = actor.id();
        entity.createdAt = occurredAt;
        entity.updatedAt = occurredAt;
        entity.applyDefinition(definition);
        return entity;
    }

    /**
     * Applies the administrative update allowlist.
     * Never touches id, equipmentNumber, equipmentCode, status, or retirement columns.
     */
    void update(EquipmentDefinition definition, AuthenticatedActor actor, Instant occurredAt) {
        applyDefinition(definition);
        updatedByUserId = actor.id();
        updatedAt = occurredAt;
    }

    /**
     * Applies a status transition.
     * When {@code status} becomes {@code RETIRED}, all three retirement columns are set atomically.
     */
    void applyStatus(
            EquipmentStatus newStatus,
            String reason,
            AuthenticatedActor actor,
            Instant occurredAt) {
        this.status = newStatus;
        this.updatedByUserId = actor.id();
        this.updatedAt = occurredAt;
        if (newStatus == EquipmentStatus.RETIRED) {
            this.retiredAt = occurredAt;
            this.retiredByUserId = actor.id();
            this.retirementReason = reason;
        }
    }

    private void applyDefinition(EquipmentDefinition definition) {
        categoryId = definition.categoryId();
        name = definition.name();
        manufacturer = definition.manufacturer();
        model = definition.model();
        serialNumber = definition.serialNumber();
        location = definition.location();
        notes = definition.notes();
        purchasedOn = definition.purchasedOn();
    }

    EquipmentDetails toDetails(String categoryName) {
        return new EquipmentDetails(
                id,
                equipmentNumber != null ? equipmentNumber : 0L,
                equipmentCode,
                categoryId,
                categoryName,
                name,
                manufacturer,
                model,
                serialNumber,
                location,
                status,
                purchasedOn,
                notes,
                retiredAt,
                retiredByUserId,
                retirementReason,
                createdByUserId,
                updatedByUserId,
                createdAt,
                updatedAt,
                version);
    }

    UUID id() { return id; }
    String equipmentCode() { return equipmentCode; }
    UUID categoryId() { return categoryId; }
    EquipmentStatus status() { return status; }
    long version() { return version; }
    Instant updatedAt() { return updatedAt; }
}
