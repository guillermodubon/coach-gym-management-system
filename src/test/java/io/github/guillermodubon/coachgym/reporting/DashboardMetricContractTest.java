package io.github.guillermodubon.coachgym.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DashboardMetricContractTest {

    @Test
    void acceptsZeroValuedMetrics() {
        assertThat(new MembershipDashboardDetails(0, 0, 0).active()).isZero();
        assertThat(new AccessDashboardDetails(0, 0).allowedToday()).isZero();
        assertThat(new EquipmentDashboardDetails(0, 0, 0).available()).isZero();
        assertThat(new IncidentDashboardDetails(0, 0, 0).open()).isZero();
        assertThat(new MaintenanceDashboardDetails(0, 0, 0).scheduled()).isZero();
        assertThat(new DashboardNotificationDetails(0).unread()).isZero();
    }

    @Test
    void rejectsNegativeCounters() {
        assertThatThrownBy(() -> new MembershipDashboardDetails(-1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccessDashboardDetails(0, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EquipmentDashboardDetails(0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DashboardNotificationDetails(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesCrossMetricConsistency() {
        assertThatThrownBy(() -> new IncidentDashboardDetails(1, 1, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MaintenanceDashboardDetails(1, 0, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesBigDecimalMoneyAndNormalizesCurrency() {
        PaymentDashboardDetails payments = new PaymentDashboardDetails(
                2, new BigDecimal("125.50"), " usd ");
        assertThat(payments.registeredAmount())
                .isEqualByComparingTo("125.50");
        assertThat(payments.currency()).isEqualTo("USD");
    }

    @Test
    void rejectsInvalidPaymentMetrics() {
        assertThatThrownBy(() -> new PaymentDashboardDetails(
                -1, BigDecimal.ZERO, "USD"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentDashboardDetails(
                0, new BigDecimal("-0.01"), "USD"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentDashboardDetails(
                0, BigDecimal.ZERO, "US"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
