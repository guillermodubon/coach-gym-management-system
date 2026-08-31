package io.github.guillermodubon.coachgym.equipment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDetails;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentCategoryDefinition;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        schema = "gym",
        name = "equipment_categories")
class EquipmentCategoryJpaEntity {

    @Id
    private UUID id;

    @Column(
            nullable = false,
            length = 120)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(
            name = "is_active",
            nullable = false)
    private boolean active;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false)
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected EquipmentCategoryJpaEntity() {
    }

    static EquipmentCategoryJpaEntity create(
            UUID id,
            EquipmentCategoryDefinition definition,
            Instant occurredAt) {

        requireId(id);
        requireDefinition(definition);
        requireOccurredAt(occurredAt);

        EquipmentCategoryJpaEntity entity =
                new EquipmentCategoryJpaEntity();

        entity.id = id;
        entity.active = true;
        entity.createdAt = occurredAt;
        entity.updatedAt = occurredAt;
        entity.version = 0L;
        entity.applyDefinition(definition);

        return entity;
    }

    void update(
            EquipmentCategoryDefinition definition,
            Instant occurredAt) {

        requireDefinition(definition);
        requireOccurredAt(occurredAt);

        applyDefinition(definition);
        updatedAt = occurredAt;
    }

    void activate(Instant occurredAt) {
        requireOccurredAt(occurredAt);

        active = true;
        updatedAt = occurredAt;
    }

    void deactivate(Instant occurredAt) {
        requireOccurredAt(occurredAt);

        active = false;
        updatedAt = occurredAt;
    }

    private void applyDefinition(
            EquipmentCategoryDefinition definition) {

        name = definition.name();
        description = definition.description();
    }

    EquipmentCategoryDetails toDetails() {
        return new EquipmentCategoryDetails(
                id,
                name,
                description,
                active,
                createdAt,
                updatedAt,
                version);
    }

    UUID id() {
        return id;
    }

    long version() {
        return version;
    }

    boolean active() {
        return active;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    private static void requireId(UUID id) {
        if (id == null) {
            throw new EquipmentValidationException(
                    "Equipment category identifier must be provided.");
        }
    }

    private static void requireDefinition(
            EquipmentCategoryDefinition definition) {

        if (definition == null) {
            throw new EquipmentValidationException(
                    "Equipment category definition must be provided.");
        }
    }

    private static void requireOccurredAt(
            Instant occurredAt) {

        if (occurredAt == null) {
            throw new EquipmentValidationException(
                    "Equipment category operation timestamp "
                            + "must be provided.");
        }
    }
}