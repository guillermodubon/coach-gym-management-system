package io.github.guillermodubon.coachgym.equipment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDetails;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentCategoryDefinition;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EquipmentCategoryJpaEntityTest {

    private static final Instant CREATED_AT =
            Instant.parse(
                    "2026-08-29T20:00:00Z");

    private static final Instant UPDATED_AT =
            Instant.parse(
                    "2026-08-29T21:00:00Z");

    @Test
    void createSetsDefaultsAndAppliesDefinition() {
        UUID id = UUID.randomUUID();

        EquipmentCategoryDefinition definition =
                EquipmentCategoryDefinition.create(
                        "Cardio",
                        "Cardio equipment");

        EquipmentCategoryJpaEntity entity =
                EquipmentCategoryJpaEntity.create(
                        id,
                        definition,
                        CREATED_AT);

        EquipmentCategoryDetails details =
                entity.toDetails();

        assertThat(details.id())
                .isEqualTo(id);

        assertThat(details.name())
                .isEqualTo("Cardio");

        assertThat(details.description())
                .isEqualTo("Cardio equipment");

        assertThat(details.active())
                .isTrue();

        assertThat(details.createdAt())
                .isEqualTo(CREATED_AT);

        assertThat(details.updatedAt())
                .isEqualTo(CREATED_AT);

        assertThat(details.version())
                .isZero();
    }

    @Test
    void updateChangesDefinitionAndUpdatedTimestamp() {
        UUID id = UUID.randomUUID();

        EquipmentCategoryJpaEntity entity =
                EquipmentCategoryJpaEntity.create(
                        id,
                        EquipmentCategoryDefinition.create(
                                "Old",
                                null),
                        CREATED_AT);

        entity.update(
                EquipmentCategoryDefinition.create(
                        "New",
                        "New desc"),
                UPDATED_AT);

        EquipmentCategoryDetails details =
                entity.toDetails();

        assertThat(details.name())
                .isEqualTo("New");

        assertThat(details.description())
                .isEqualTo("New desc");

        assertThat(details.createdAt())
                .isEqualTo(CREATED_AT);

        assertThat(details.updatedAt())
                .isEqualTo(UPDATED_AT);
    }

    @Test
    void activateSetsActiveAndUpdatesTimestamp() {
        UUID id = UUID.randomUUID();

        EquipmentCategoryJpaEntity entity =
                EquipmentCategoryJpaEntity.create(
                        id,
                        EquipmentCategoryDefinition.create(
                                "Weights",
                                null),
                        CREATED_AT);

        entity.deactivate(UPDATED_AT);

        assertThat(entity.active())
                .isFalse();

        Instant reactivatedAt =
                Instant.parse(
                        "2026-08-29T22:00:00Z");

        entity.activate(reactivatedAt);

        EquipmentCategoryDetails details =
                entity.toDetails();

        assertThat(details.active())
                .isTrue();

        assertThat(details.createdAt())
                .isEqualTo(CREATED_AT);

        assertThat(details.updatedAt())
                .isEqualTo(reactivatedAt);
    }

    @Test
    void deactivateSetsInactiveAndUpdatesTimestamp() {
        UUID id = UUID.randomUUID();

        EquipmentCategoryJpaEntity entity =
                EquipmentCategoryJpaEntity.create(
                        id,
                        EquipmentCategoryDefinition.create(
                                "Weights",
                                null),
                        CREATED_AT);

        entity.deactivate(UPDATED_AT);

        EquipmentCategoryDetails details =
                entity.toDetails();

        assertThat(details.active())
                .isFalse();

        assertThat(details.createdAt())
                .isEqualTo(CREATED_AT);

        assertThat(details.updatedAt())
                .isEqualTo(UPDATED_AT);
    }

    @Test
    void toDetailsMapsAllFields() {
        UUID id = UUID.randomUUID();

        EquipmentCategoryJpaEntity entity =
                EquipmentCategoryJpaEntity.create(
                        id,
                        EquipmentCategoryDefinition.create(
                                "Stretching",
                                "Flexibility area"),
                        CREATED_AT);

        EquipmentCategoryDetails details =
                entity.toDetails();

        assertThat(details.id())
                .isEqualTo(id);

        assertThat(details.name())
                .isEqualTo("Stretching");

        assertThat(details.description())
                .isEqualTo("Flexibility area");

        assertThat(details.active())
                .isTrue();

        assertThat(details.createdAt())
                .isEqualTo(CREATED_AT);

        assertThat(details.updatedAt())
                .isEqualTo(CREATED_AT);

        assertThat(details.version())
                .isZero();
    }
}
