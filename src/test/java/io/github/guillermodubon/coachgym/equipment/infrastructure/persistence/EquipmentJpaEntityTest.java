package io.github.guillermodubon.coachgym.equipment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentJpaEntityTest {

    private static final UUID CAT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2025-06-01T09:00:00Z");
    private static final AuthenticatedActor ACTOR = new AuthenticatedActor(ACTOR_ID, "admin@gym.com");

    private EquipmentDefinition simpleDefinition() {
        return EquipmentDefinition.create(CAT_ID, "Treadmill", null, null, null, null, null, null);
    }

    @Test
    void register_setsAvailableStatus_andActorFields() {
        EquipmentJpaEntity entity = EquipmentJpaEntity.register(
                UUID.randomUUID(), simpleDefinition(), ACTOR, NOW);

        assertThat(entity.status()).isEqualTo(EquipmentStatus.AVAILABLE);
        EquipmentDetails details = entity.toDetails("Cardio");
        assertThat(details.createdByUserId()).isEqualTo(ACTOR_ID);
        assertThat(details.updatedByUserId()).isEqualTo(ACTOR_ID);
        assertThat(details.createdAt()).isEqualTo(NOW);
        assertThat(details.updatedAt()).isEqualTo(NOW);
        assertThat(details.retiredAt()).isNull();
        assertThat(details.retiredByUserId()).isNull();
        assertThat(details.retirementReason()).isNull();
    }

    @Test
    void register_appliesAllDefinitionFields() {
        LocalDate purchased = LocalDate.of(2023, 3, 10);
        EquipmentDefinition def = EquipmentDefinition.create(
                CAT_ID, "Bike", "Acme", "B100", "SN-XYZ", "Room B", "Service due", purchased);
        EquipmentJpaEntity entity = EquipmentJpaEntity.register(UUID.randomUUID(), def, ACTOR, NOW);

        EquipmentDetails d = entity.toDetails("Weights");
        assertThat(d.categoryId()).isEqualTo(CAT_ID);
        assertThat(d.name()).isEqualTo("Bike");
        assertThat(d.manufacturer()).isEqualTo("Acme");
        assertThat(d.model()).isEqualTo("B100");
        assertThat(d.serialNumber()).isEqualTo("SN-XYZ");
        assertThat(d.location()).isEqualTo("Room B");
        assertThat(d.notes()).isEqualTo("Service due");
        assertThat(d.purchasedOn()).isEqualTo(purchased);
    }

    @Test
    void update_changesAllowlistedFields_andSetsUpdatedBy() {
        UUID id = UUID.randomUUID();
        EquipmentJpaEntity entity = EquipmentJpaEntity.register(id, simpleDefinition(), ACTOR, NOW);

        Instant later = NOW.plusSeconds(3600);
        AuthenticatedActor editor = new AuthenticatedActor(UUID.randomUUID(), "editor@gym.com");
        EquipmentDefinition updated = EquipmentDefinition.create(
                CAT_ID, "Updated Treadmill", "NewBrand", null, null, null, null, null);
        entity.update(updated, editor, later);

        EquipmentDetails d = entity.toDetails("Cardio");
        assertThat(d.name()).isEqualTo("Updated Treadmill");
        assertThat(d.manufacturer()).isEqualTo("NewBrand");
        assertThat(d.updatedByUserId()).isEqualTo(editor.id());
        assertThat(d.updatedAt()).isEqualTo(later);
        // Status must not change
        assertThat(d.status()).isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test
    void applyStatus_outOfService_doesNotSetRetirementColumns() {
        EquipmentJpaEntity entity = EquipmentJpaEntity.register(
                UUID.randomUUID(), simpleDefinition(), ACTOR, NOW);

        entity.applyStatus(EquipmentStatus.OUT_OF_SERVICE, "Broken belt", ACTOR, NOW);

        EquipmentDetails d = entity.toDetails("Cardio");
        assertThat(d.status()).isEqualTo(EquipmentStatus.OUT_OF_SERVICE);
        assertThat(d.retiredAt()).isNull();
        assertThat(d.retiredByUserId()).isNull();
        assertThat(d.retirementReason()).isNull();
    }

    @Test
    void applyStatus_retired_setsRetirementColumnsAtomically() {
        EquipmentJpaEntity entity = EquipmentJpaEntity.register(
                UUID.randomUUID(), simpleDefinition(), ACTOR, NOW);

        entity.applyStatus(EquipmentStatus.RETIRED, "End of life", ACTOR, NOW);

        EquipmentDetails d = entity.toDetails("Cardio");
        assertThat(d.status()).isEqualTo(EquipmentStatus.RETIRED);
        assertThat(d.retiredAt()).isEqualTo(NOW);
        assertThat(d.retiredByUserId()).isEqualTo(ACTOR_ID);
        assertThat(d.retirementReason()).isEqualTo("End of life");
    }

    @Test
    void toDetails_exposesGeneratedCodeFields_asNullBeforeDbRefresh() {
        // equipmentNumber and equipmentCode are GENERATED ALWAYS columns — they are
        // null/zero before the entity is persisted and refreshed. This test documents
        // that expectation for reader clarity.
        EquipmentJpaEntity entity = EquipmentJpaEntity.register(
                UUID.randomUUID(), simpleDefinition(), ACTOR, NOW);

        EquipmentDetails d = entity.toDetails("Cardio");
        assertThat(d.equipmentCode()).isNull();
        assertThat(d.equipmentNumber()).isZero();
    }
}
