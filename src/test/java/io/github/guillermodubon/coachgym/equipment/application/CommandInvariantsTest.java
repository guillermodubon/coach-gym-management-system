package io.github.guillermodubon.coachgym.equipment.application;

import io.github.guillermodubon.coachgym.equipment.application.command.ActivateEquipmentCategoryCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.DeactivateEquipmentCategoryCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.MarkAvailableCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.MarkOutOfServiceCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.RegisterEquipmentCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.RetireEquipmentCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.UpdateEquipmentCategoryCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.UpdateEquipmentCommand;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentCategoryDefinition;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentDefinition;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandInvariantsTest {

    private static final UUID ID = UUID.randomUUID();
    private static final EquipmentCategoryDefinition CAT_DEF =
            EquipmentCategoryDefinition.create("Cardio", null);
    private static final EquipmentDefinition EQ_DEF =
            EquipmentDefinition.create(UUID.randomUUID(), "Bike", null, null, null, null, null, null);

    // ── RegisterEquipmentCommand ──────────────────────────────────────────────

    @Test
    void registerEquipment_nullDefinition_throws() {
        assertThatThrownBy(() -> new RegisterEquipmentCommand(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── UpdateEquipmentCommand ────────────────────────────────────────────────

    @Test
    void updateEquipment_nullId_throws() {
        assertThatThrownBy(() -> new UpdateEquipmentCommand(null, EQ_DEF, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateEquipment_nullDefinition_throws() {
        assertThatThrownBy(() -> new UpdateEquipmentCommand(ID, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateEquipment_negativeVersion_throws() {
        assertThatThrownBy(() -> new UpdateEquipmentCommand(ID, EQ_DEF, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version");
    }

    @Test
    void updateEquipment_zeroVersion_isValid() {
        new UpdateEquipmentCommand(ID, EQ_DEF, 0);
    }

    // ── UpdateEquipmentCategoryCommand ────────────────────────────────────────

    @Test
    void updateCategory_nullId_throws() {
        assertThatThrownBy(() -> new UpdateEquipmentCategoryCommand(null, CAT_DEF, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateCategory_negativeVersion_throws() {
        assertThatThrownBy(() -> new UpdateEquipmentCategoryCommand(ID, CAT_DEF, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version");
    }

    // ── ActivateEquipmentCategoryCommand ──────────────────────────────────────

    @Test
    void activateCategory_nullId_throws() {
        assertThatThrownBy(() -> new ActivateEquipmentCategoryCommand(null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activateCategory_negativeVersion_throws() {
        assertThatThrownBy(() -> new ActivateEquipmentCategoryCommand(ID, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version");
    }

    // ── DeactivateEquipmentCategoryCommand ────────────────────────────────────

    @Test
    void deactivateCategory_nullId_throws() {
        assertThatThrownBy(() -> new DeactivateEquipmentCategoryCommand(null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deactivateCategory_negativeVersion_throws() {
        assertThatThrownBy(() -> new DeactivateEquipmentCategoryCommand(ID, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version");
    }

    // ── MarkOutOfServiceCommand ───────────────────────────────────────────────

    @Test
    void markOutOfService_nullId_throws() {
        assertThatThrownBy(() -> new MarkOutOfServiceCommand(null, "reason", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markOutOfService_nullReason_throws() {
        assertThatThrownBy(() -> new MarkOutOfServiceCommand(ID, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reason");
    }

    @Test
    void markOutOfService_blankReason_throws() {
        assertThatThrownBy(() -> new MarkOutOfServiceCommand(ID, "  ", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reason");
    }

    @Test
    void markOutOfService_negativeVersion_throws() {
        assertThatThrownBy(() -> new MarkOutOfServiceCommand(ID, "reason", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version");
    }

    // ── MarkAvailableCommand ──────────────────────────────────────────────────

    @Test
    void markAvailable_nullId_throws() {
        assertThatThrownBy(() -> new MarkAvailableCommand(null, "reason", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markAvailable_blankReason_throws() {
        assertThatThrownBy(() -> new MarkAvailableCommand(ID, "  ", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reason");
    }

    @Test
    void markAvailable_negativeVersion_throws() {
        assertThatThrownBy(() -> new MarkAvailableCommand(ID, "reason", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version");
    }

    // ── RetireEquipmentCommand ────────────────────────────────────────────────

    @Test
    void retire_nullId_throws() {
        assertThatThrownBy(() -> new RetireEquipmentCommand(null, "reason", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retire_nullReason_throws() {
        assertThatThrownBy(() -> new RetireEquipmentCommand(ID, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reason");
    }

    @Test
    void retire_negativeVersion_throws() {
        assertThatThrownBy(() -> new RetireEquipmentCommand(ID, "reason", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version");
    }
}
