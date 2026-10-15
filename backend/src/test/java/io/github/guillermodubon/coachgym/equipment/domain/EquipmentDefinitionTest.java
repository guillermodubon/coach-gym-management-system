package io.github.guillermodubon.coachgym.equipment.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipmentDefinitionTest {

    private static final UUID CATEGORY_ID = UUID.randomUUID();

    // ── categoryId ────────────────────────────────────────────────────────────

    @Test
    void nullCategoryId_isRejected() {
        assertThatThrownBy(() -> EquipmentDefinition.create(
                null, "Treadmill", null, null, null, null, null, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("category");
    }

    // ── name validation ───────────────────────────────────────────────────────

    @Test
    void validName_isAccepted() {
        EquipmentDefinition def = EquipmentDefinition.create(
                CATEGORY_ID, "Treadmill Pro", null, null, null, null, null, null);
        assertThat(def.name()).isEqualTo("Treadmill Pro");
    }

    @Test
    void name_isTrimmed() {
        EquipmentDefinition def = EquipmentDefinition.create(
                CATEGORY_ID, "  Treadmill  ", null, null, null, null, null, null);
        assertThat(def.name()).isEqualTo("Treadmill");
    }

    @Test
    void nullName_isRejected() {
        assertThatThrownBy(() -> EquipmentDefinition.create(
                CATEGORY_ID, null, null, null, null, null, null, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("Equipment name");
    }

    @Test
    void blankName_isRejected() {
        assertThatThrownBy(() -> EquipmentDefinition.create(
                CATEGORY_ID, "  ", null, null, null, null, null, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("Equipment name");
    }

    @Test
    void nameAtMaxLength_isAccepted() {
        String name = "a".repeat(160);
        EquipmentDefinition def = EquipmentDefinition.create(
                CATEGORY_ID, name, null, null, null, null, null, null);
        assertThat(def.name()).hasSize(160);
    }

    @Test
    void nameExceedingMaxLength_isRejected() {
        assertThatThrownBy(() -> EquipmentDefinition.create(
                CATEGORY_ID, "a".repeat(161), null, null, null, null, null, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("160");
    }

    // ── optional string fields ────────────────────────────────────────────────

    @Test
    void nullOptionalFields_normalizeToNull() {
        EquipmentDefinition def = EquipmentDefinition.create(
                CATEGORY_ID, "Bike", null, null, null, null, null, null);
        assertThat(def.manufacturer()).isNull();
        assertThat(def.model()).isNull();
        assertThat(def.serialNumber()).isNull();
        assertThat(def.location()).isNull();
        assertThat(def.notes()).isNull();
    }

    @Test
    void blankOptionalFields_normalizeToNull() {
        EquipmentDefinition def = EquipmentDefinition.create(
                CATEGORY_ID, "Bike", "  ", "  ", "  ", "  ", "  ", null);
        assertThat(def.manufacturer()).isNull();
        assertThat(def.model()).isNull();
        assertThat(def.serialNumber()).isNull();
        assertThat(def.location()).isNull();
        assertThat(def.notes()).isNull();
    }

    @Test
    void optionalFields_areTrimmed() {
        EquipmentDefinition def = EquipmentDefinition.create(
                CATEGORY_ID, "Bike", "  Acme  ", "  X200  ", "  SN-001  ", "  Room A  ", "  Note  ", null);
        assertThat(def.manufacturer()).isEqualTo("Acme");
        assertThat(def.model()).isEqualTo("X200");
        assertThat(def.serialNumber()).isEqualTo("SN-001");
        assertThat(def.location()).isEqualTo("Room A");
        assertThat(def.notes()).isEqualTo("Note");
    }

    @Test
    void manufacturerExceedingMaxLength_isRejected() {
        assertThatThrownBy(() -> EquipmentDefinition.create(
                CATEGORY_ID, "Bike", "a".repeat(121), null, null, null, null, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("120");
    }

    @Test
    void modelExceedingMaxLength_isRejected() {
        assertThatThrownBy(() -> EquipmentDefinition.create(
                CATEGORY_ID, "Bike", null, "a".repeat(121), null, null, null, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("120");
    }

    @Test
    void serialNumberExceedingMaxLength_isRejected() {
        assertThatThrownBy(() -> EquipmentDefinition.create(
                CATEGORY_ID, "Bike", null, null, "a".repeat(121), null, null, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("120");
    }

    @Test
    void locationExceedingMaxLength_isRejected() {
        assertThatThrownBy(() -> EquipmentDefinition.create(
                CATEGORY_ID, "Bike", null, null, null, "a".repeat(161), null, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("160");
    }

    // ── purchasedOn ───────────────────────────────────────────────────────────

    @Test
    void nullPurchasedOn_isAccepted() {
        EquipmentDefinition def = EquipmentDefinition.create(
                CATEGORY_ID, "Bike", null, null, null, null, null, null);
        assertThat(def.purchasedOn()).isNull();
    }

    @Test
    void pastPurchasedOn_isAccepted() {
        LocalDate past = LocalDate.of(2020, 1, 15);
        EquipmentDefinition def = EquipmentDefinition.create(
                CATEGORY_ID, "Bike", null, null, null, null, null, past);
        assertThat(def.purchasedOn()).isEqualTo(past);
    }

    @Test
    void futurePurchasedOn_isAcceptedByDomain() {
        // V8 does not prohibit future dates; application policy may decide later.
        LocalDate future = LocalDate.now().plusYears(1);
        EquipmentDefinition def = EquipmentDefinition.create(
                CATEGORY_ID, "Bike", null, null, null, null, null, future);
        assertThat(def.purchasedOn()).isEqualTo(future);
    }

    // ── complete valid definition ─────────────────────────────────────────────

    @Test
    void completeDefinition_isCreatedCorrectly() {
        LocalDate purchased = LocalDate.of(2023, 6, 1);
        EquipmentDefinition def = EquipmentDefinition.create(
                CATEGORY_ID,
                "Commercial Treadmill",
                "LifeFitness",
                "T5",
                "SN-XYZ-001",
                "Cardio Floor - Row A",
                "Annual service due in June",
                purchased);
        assertThat(def.categoryId()).isEqualTo(CATEGORY_ID);
        assertThat(def.name()).isEqualTo("Commercial Treadmill");
        assertThat(def.manufacturer()).isEqualTo("LifeFitness");
        assertThat(def.model()).isEqualTo("T5");
        assertThat(def.serialNumber()).isEqualTo("SN-XYZ-001");
        assertThat(def.location()).isEqualTo("Cardio Floor - Row A");
        assertThat(def.notes()).isEqualTo("Annual service due in June");
        assertThat(def.purchasedOn()).isEqualTo(purchased);
    }
}
