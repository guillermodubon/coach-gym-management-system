package io.github.guillermodubon.coachgym.equipment.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipmentCategoryDefinitionTest {

    // ── name validation ───────────────────────────────────────────────────────

    @Test
    void validName_isAccepted() {
        EquipmentCategoryDefinition def = EquipmentCategoryDefinition.create("Cardio", null);
        assertThat(def.name()).isEqualTo("Cardio");
    }

    @Test
    void name_isTrimmed() {
        EquipmentCategoryDefinition def = EquipmentCategoryDefinition.create("  Cardio  ", null);
        assertThat(def.name()).isEqualTo("Cardio");
    }

    @Test
    void nullName_isRejected() {
        assertThatThrownBy(() -> EquipmentCategoryDefinition.create(null, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("Category name");
    }

    @Test
    void blankName_isRejected() {
        assertThatThrownBy(() -> EquipmentCategoryDefinition.create("   ", null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("Category name");
    }

    @Test
    void nameAtMaxLength_isAccepted() {
        String name = "a".repeat(120);
        EquipmentCategoryDefinition def = EquipmentCategoryDefinition.create(name, null);
        assertThat(def.name()).hasSize(120);
    }

    @Test
    void nameExceedingMaxLength_isRejected() {
        String name = "a".repeat(121);
        assertThatThrownBy(() -> EquipmentCategoryDefinition.create(name, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("120");
    }

    // ── description validation ────────────────────────────────────────────────

    @Test
    void nullDescription_normalizesToNull() {
        EquipmentCategoryDefinition def = EquipmentCategoryDefinition.create("Cardio", null);
        assertThat(def.description()).isNull();
    }

    @Test
    void blankDescription_normalizesToNull() {
        EquipmentCategoryDefinition def = EquipmentCategoryDefinition.create("Cardio", "   ");
        assertThat(def.description()).isNull();
    }

    @Test
    void description_isTrimmed() {
        EquipmentCategoryDefinition def = EquipmentCategoryDefinition.create("Cardio", "  Desc  ");
        assertThat(def.description()).isEqualTo("Desc");
    }

    @Test
    void validDescription_isAccepted() {
        EquipmentCategoryDefinition def = EquipmentCategoryDefinition.create("Cardio", "Weight training equipment");
        assertThat(def.description()).isEqualTo("Weight training equipment");
    }
}
