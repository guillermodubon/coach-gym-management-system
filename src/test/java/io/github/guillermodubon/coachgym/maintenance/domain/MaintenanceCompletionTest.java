package io.github.guillermodubon.coachgym.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.EquipmentMaintenanceOutcome;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MaintenanceCompletionTest {

    private static final Instant STARTED = Instant.parse("2026-09-08T14:00:00Z");

    @Test
    void normalizesValidCompletion() {
        MaintenanceCompletion completion = new MaintenanceCompletion(
                STARTED.plusSeconds(3600), " Replaced controller. ",
                new BigDecimal("140"), "usd",
                EquipmentMaintenanceOutcome.AVAILABLE);

        completion.validateAgainst(STARTED);
        completion.validateCurrency("USD");
        assertThat(completion.actionsTaken()).isEqualTo("Replaced controller.");
        assertThat(completion.actualCost()).isEqualByComparingTo("140.00");
        assertThat(completion.currency()).isEqualTo("USD");
    }

    @Test
    void rejectsCompletionBeforeStart() {
        MaintenanceCompletion completion = new MaintenanceCompletion(
                STARTED.minusSeconds(1), "Checked.", null, "USD",
                EquipmentMaintenanceOutcome.OUT_OF_SERVICE);
        assertThatThrownBy(() -> completion.validateAgainst(STARTED))
                .isInstanceOf(MaintenanceValidationException.class)
                .hasMessageContaining("before the start");
    }

    @Test
    void rejectsMissingActionsOutcomeAndCurrencyMismatch() {
        assertThatThrownBy(() -> new MaintenanceCompletion(
                STARTED, " ", null, "USD", EquipmentMaintenanceOutcome.AVAILABLE))
                .isInstanceOf(MaintenanceValidationException.class);
        assertThatThrownBy(() -> new MaintenanceCompletion(
                STARTED, "Checked.", null, "USD", null))
                .isInstanceOf(MaintenanceValidationException.class);

        MaintenanceCompletion completion = new MaintenanceCompletion(
                STARTED, "Checked.", null, "USD",
                EquipmentMaintenanceOutcome.AVAILABLE);
        assertThatThrownBy(() -> completion.validateCurrency("EUR"))
                .isInstanceOf(MaintenanceValidationException.class)
                .hasMessageContaining("must match");
    }
}
