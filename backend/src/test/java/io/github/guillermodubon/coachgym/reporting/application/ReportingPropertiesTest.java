package io.github.guillermodubon.coachgym.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ReportingPropertiesTest {

    @Test
    void suppliesMaximumPeriodDefault() {
        ReportingProperties properties = new ReportingProperties();
        assertThat(properties.getMaximumPeriodDays()).isEqualTo(366);
    }

    @Test
    void acceptsConfiguredMaximumPeriod() {
        ReportingProperties properties = new ReportingProperties();
        properties.setMaximumPeriodDays(90);
        assertThat(properties.getMaximumPeriodDays()).isEqualTo(90);
    }

    @Test
    void rejectsInvalidMaximumPeriod() {
        ReportingProperties properties = new ReportingProperties();
        assertThatThrownBy(() -> properties.setMaximumPeriodDays(0))
                .isInstanceOf(ReportingValidationException.class);
        assertThatThrownBy(() -> properties.setMaximumPeriodDays(3_661))
                .isInstanceOf(ReportingValidationException.class);
    }
}
