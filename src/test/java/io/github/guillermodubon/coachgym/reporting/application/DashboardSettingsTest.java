package io.github.guillermodubon.coachgym.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DashboardSettingsTest {

    @Test
    void acceptsAndNormalizesBusinessSettings() {
        DashboardSettings settings = new DashboardSettings(7, " usd ");
        assertThat(settings.membershipExpirationWarningDays()).isEqualTo(7);
        assertThat(settings.currency()).isEqualTo("USD");
    }

    @Test
    void acceptsBoundaryWarningWindows() {
        assertThat(new DashboardSettings(0, "USD")
                .membershipExpirationWarningDays()).isZero();
        assertThat(new DashboardSettings(90, "USD")
                .membershipExpirationWarningDays()).isEqualTo(90);
    }

    @Test
    void rejectsInvalidSettings() {
        assertThatThrownBy(() -> new DashboardSettings(-1, "USD"))
                .isInstanceOf(ReportingValidationException.class);
        assertThatThrownBy(() -> new DashboardSettings(91, "USD"))
                .isInstanceOf(ReportingValidationException.class);
        assertThatThrownBy(() -> new DashboardSettings(7, "US"))
                .isInstanceOf(ReportingValidationException.class);
        assertThatThrownBy(() -> new DashboardSettings(7, " "))
                .isInstanceOf(ReportingValidationException.class);
    }
}
