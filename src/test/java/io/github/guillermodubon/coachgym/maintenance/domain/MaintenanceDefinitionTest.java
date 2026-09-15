package io.github.guillermodubon.coachgym.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaintenanceDefinitionTest {

    private static final UUID EQUIPMENT_ID = UUID.randomUUID();

    @Test
    void normalizesValidPreventiveDefinition() {
        MaintenanceDefinition definition = new MaintenanceDefinition(
                EQUIPMENT_ID, null, MaintenanceType.PREVENTIVE,
                LocalDate.of(2026, 9, 8), " Provider ", " Technician ",
                new BigDecimal("125"), " usd ", " Inspect belt. ", null);

        assertThat(definition.providerName()).isEqualTo("Provider");
        assertThat(definition.technicianName()).isEqualTo("Technician");
        assertThat(definition.estimatedCost()).isEqualByComparingTo("125.00");
        assertThat(definition.currency()).isEqualTo("USD");
        assertThat(definition.notes()).isEqualTo("Inspect belt.");
    }

    @Test
    void allowsCorrectiveDefinitionWithOrWithoutIncident() {
        UUID incidentId = UUID.randomUUID();
        assertThat(validCorrective(incidentId).incidentId()).isEqualTo(incidentId);
        assertThat(validCorrective(null).incidentId()).isNull();
    }

    @Test
    void rejectsPreventiveDefinitionLinkedToIncident() {
        assertThatThrownBy(() -> new MaintenanceDefinition(
                EQUIPMENT_ID, UUID.randomUUID(), MaintenanceType.PREVENTIVE,
                LocalDate.now(), null, null, null, "USD", null, null))
                .isInstanceOf(MaintenanceValidationException.class)
                .hasMessage("Preventive maintenance cannot be linked to an incident.");
    }

    @Test
    void rejectsNegativeOrOverPreciseEstimatedCost() {
        assertThatThrownBy(() -> definitionWithCost(new BigDecimal("-0.01")))
                .isInstanceOf(MaintenanceValidationException.class)
                .hasMessageContaining("must not be negative");
        assertThatThrownBy(() -> definitionWithCost(new BigDecimal("1.001")))
                .isInstanceOf(MaintenanceValidationException.class)
                .hasMessageContaining("more than 2 decimal places");
    }

    @Test
    void rejectsInvalidCurrency() {
        assertThatThrownBy(() -> new MaintenanceDefinition(
                EQUIPMENT_ID, null, MaintenanceType.PREVENTIVE,
                LocalDate.now(), null, null, null, "US", null, null))
                .isInstanceOf(MaintenanceValidationException.class)
                .hasMessageContaining("exactly 3 uppercase letters");
    }

    private static MaintenanceDefinition validCorrective(UUID incidentId) {
        return new MaintenanceDefinition(
                EQUIPMENT_ID, incidentId, MaintenanceType.CORRECTIVE,
                LocalDate.now(), null, null, null, "USD", null, null);
    }

    private static MaintenanceDefinition definitionWithCost(BigDecimal cost) {
        return new MaintenanceDefinition(
                EQUIPMENT_ID, null, MaintenanceType.PREVENTIVE,
                LocalDate.now(), null, null, cost, "USD", null, null);
    }
}
