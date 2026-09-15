package io.github.guillermodubon.coachgym.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.EquipmentMaintenanceOutcome;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceType;
import io.github.guillermodubon.coachgym.maintenance.application.command.CancelMaintenanceCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.CompleteMaintenanceCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.ScheduleMaintenanceCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.StartMaintenanceCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.UpdateScheduledMaintenanceCommand;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaintenanceCommandTest {
    private static final UUID MAINTENANCE_ID = UUID.randomUUID();
    private static final UUID EQUIPMENT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

    @Test
    void scheduleCommandProducesNormalizedDefinition() {
        var command = new ScheduleMaintenanceCommand(
                EQUIPMENT_ID, null, MaintenanceType.PREVENTIVE,
                LocalDate.of(2026, 9, 10), " Provider ", null,
                new BigDecimal("10"), "usd", " Notes ", null);
        assertThat(command.definition().providerName()).isEqualTo("Provider");
        assertThat(command.definition().currency()).isEqualTo("USD");
    }

    @Test
    void updateCommandRejectsNegativeVersion() {
        assertThatThrownBy(() -> new UpdateScheduledMaintenanceCommand(
                MAINTENANCE_ID, LocalDate.now(), null, null,
                null, "USD", null, null, -1))
                .isInstanceOf(MaintenanceValidationException.class);
    }

    @Test
    void startCommandNormalizesReasonAndValidatesVersions() {
        var command = new StartMaintenanceCommand(
                MAINTENANCE_ID, NOW, " Start work. ", 0, 2);
        assertThat(command.reason()).isEqualTo("Start work.");
        assertThatThrownBy(() -> new StartMaintenanceCommand(
                MAINTENANCE_ID, NOW, "Start.", -1, 0))
                .isInstanceOf(MaintenanceValidationException.class);
    }

    @Test
    void completeCommandProducesValidatedCompletion() {
        var command = new CompleteMaintenanceCommand(
                MAINTENANCE_ID, NOW, " Replaced belt. ",
                new BigDecimal("25"), "usd",
                EquipmentMaintenanceOutcome.AVAILABLE, 1, 3);
        assertThat(command.completion().actionsTaken()).isEqualTo("Replaced belt.");
        assertThat(command.completion().currency()).isEqualTo("USD");
    }

    @Test
    void cancellationRequiresEquipmentVersionWhenOutcomeIsPresent() {
        assertThatThrownBy(() -> new CancelMaintenanceCommand(
                MAINTENANCE_ID, "Cancel.",
                EquipmentMaintenanceOutcome.OUT_OF_SERVICE, 1, null))
                .isInstanceOf(MaintenanceValidationException.class)
                .hasMessageContaining("Equipment version is required");
    }
}
