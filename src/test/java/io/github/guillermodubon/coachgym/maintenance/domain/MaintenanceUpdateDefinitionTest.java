package io.github.guillermodubon.coachgym.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MaintenanceUpdateDefinitionTest {

    @Test
    void normalizesScheduledUpdate() {
        MaintenanceUpdateDefinition definition = new MaintenanceUpdateDefinition(
                LocalDate.of(2026, 9, 10), " Provider ", " Tech ",
                new BigDecimal("20"), "usd", " Notes ", null);

        assertThat(definition.providerName()).isEqualTo("Provider");
        assertThat(definition.technicianName()).isEqualTo("Tech");
        assertThat(definition.estimatedCost()).isEqualByComparingTo("20.00");
        assertThat(definition.currency()).isEqualTo("USD");
        assertThat(definition.notes()).isEqualTo("Notes");
    }

    @Test
    void requiresScheduledDate() {
        assertThatThrownBy(() -> new MaintenanceUpdateDefinition(
                null, null, null, null, "USD", null, null))
                .isInstanceOf(MaintenanceValidationException.class)
                .hasMessage("Scheduled date is required.");
    }
}
