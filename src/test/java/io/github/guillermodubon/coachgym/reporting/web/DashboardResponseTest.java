package io.github.guillermodubon.coachgym.reporting.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.reporting.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DashboardResponseTest {

    @Test
    void mapsCompleteAdministratorDashboard() {
        OperationalDashboardResponse response = OperationalDashboardResponse.from(
                OperationalDashboardDetails.forAdministrator(
                        Instant.parse("2026-09-05T18:00:00Z"),
                        new DashboardPeriodDetails(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5)),
                        new MembershipDashboardDetails(10, 2, 3),
                        new AccessDashboardDetails(20, 1),
                        new PaymentDashboardDetails(4, new BigDecimal("200.00"), "USD"),
                        new EquipmentDashboardDetails(12, 2, 1),
                        new IncidentDashboardDetails(2, 1, 1),
                        new MaintenanceDashboardDetails(3, 1, 1),
                        new DashboardNotificationDetails(5)));
        assertThat(response.payments()).isNotNull();
        assertThat(response.payments().registeredAmount()).isEqualByComparingTo("200.00");
        assertThat(response.notifications().unread()).isEqualTo(5);
    }

    @Test
    void preservesNullAdministrativeSectionsForReceptionist() {
        OperationalDashboardResponse response = OperationalDashboardResponse.from(
                OperationalDashboardDetails.forReceptionist(
                        Instant.parse("2026-09-05T18:00:00Z"),
                        new DashboardPeriodDetails(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5)),
                        new MembershipDashboardDetails(10, 2, 3),
                        new AccessDashboardDetails(20, 1),
                        new DashboardNotificationDetails(5)));
        assertThat(response.payments()).isNull();
        assertThat(response.equipment()).isNull();
        assertThat(response.incidents()).isNull();
        assertThat(response.maintenance()).isNull();
    }
}
