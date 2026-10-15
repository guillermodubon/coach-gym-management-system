package io.github.guillermodubon.coachgym.plan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.plan.DurationUnit;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PlanDefinitionTest {

    @Test
    void normalizesDefinitionAndMoney() {
        PlanDefinition definition = PlanDefinition.create(
                "  Monthly plan ",
                "  Access to all equipment. ",
                1,
                DurationUnit.MONTH,
                new BigDecimal("25"),
                "usd");

        assertThat(definition.name()).isEqualTo("Monthly plan");
        assertThat(definition.description()).isEqualTo("Access to all equipment.");
        assertThat(definition.listPrice()).isEqualByComparingTo("25.00");
        assertThat(definition.currency()).isEqualTo("USD");
    }

    @Test
    void rejectsInvalidDurationAndCurrency() {
        assertThatThrownBy(() -> PlanDefinition.create(
                "Monthly",
                null,
                0,
                DurationUnit.MONTH,
                new BigDecimal("25.00"),
                "USD"))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("duration");

        assertThatThrownBy(() -> PlanDefinition.create(
                "Monthly",
                null,
                1,
                DurationUnit.MONTH,
                new BigDecimal("25.00"),
                "US"))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("Currency");
    }

    @Test
    void rejectsNegativeOrOverPrecisionPrice() {
        assertThatThrownBy(() -> PlanDefinition.create(
                "Monthly",
                null,
                1,
                DurationUnit.MONTH,
                new BigDecimal("-1.00"),
                "USD"))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("negative");

        assertThatThrownBy(() -> PlanDefinition.create(
                "Monthly",
                null,
                1,
                DurationUnit.MONTH,
                new BigDecimal("25.999"),
                "USD"))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("decimal");
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> PlanDefinition.create(
                " ",
                null,
                1,
                DurationUnit.MONTH,
                new BigDecimal("25.00"),
                "USD"))
                .isInstanceOf(PlanValidationException.class)
                .hasMessage("Plan name must not be blank.");
    }
}
