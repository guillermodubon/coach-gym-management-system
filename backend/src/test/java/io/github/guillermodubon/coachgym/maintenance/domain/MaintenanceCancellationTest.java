package io.github.guillermodubon.coachgym.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.EquipmentMaintenanceOutcome;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import org.junit.jupiter.api.Test;

class MaintenanceCancellationTest {

    @Test
    void scheduledCancellationRequiresNoEquipmentOutcome() {
        MaintenanceCancellation cancellation =
                new MaintenanceCancellation(" Provider unavailable. ", null);
        cancellation.validateFor(MaintenanceStatus.SCHEDULED);
        assertThat(cancellation.reason()).isEqualTo("Provider unavailable.");
    }

    @Test
    void inProgressCancellationRequiresEquipmentOutcome() {
        MaintenanceCancellation cancellation = new MaintenanceCancellation(
                "Unsafe to continue.", EquipmentMaintenanceOutcome.OUT_OF_SERVICE);
        cancellation.validateFor(MaintenanceStatus.IN_PROGRESS);
    }

    @Test
    void rejectsOutcomeForScheduledAndMissingOutcomeForInProgress() {
        assertThatThrownBy(() -> new MaintenanceCancellation(
                "Cancelled.", EquipmentMaintenanceOutcome.AVAILABLE)
                .validateFor(MaintenanceStatus.SCHEDULED))
                .isInstanceOf(MaintenanceValidationException.class)
                .hasMessageContaining("must not define");
        assertThatThrownBy(() -> new MaintenanceCancellation(
                "Cancelled.", null)
                .validateFor(MaintenanceStatus.IN_PROGRESS))
                .isInstanceOf(MaintenanceValidationException.class)
                .hasMessageContaining("requires an equipment outcome");
    }

    @Test
    void rejectsTerminalCancellationAndBlankReason() {
        assertThatThrownBy(() -> new MaintenanceCancellation(" ", null))
                .isInstanceOf(MaintenanceValidationException.class);
        assertThatThrownBy(() -> new MaintenanceCancellation("Again.", null)
                .validateFor(MaintenanceStatus.COMPLETED))
                .isInstanceOf(MaintenanceValidationException.class)
                .hasMessageContaining("Terminal maintenance");
    }
}
