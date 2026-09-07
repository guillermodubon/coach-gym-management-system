package io.github.guillermodubon.coachgym.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class OperationalDashboardDetailsTest {

    private static final Instant GENERATED_AT =
            Instant.parse("2026-09-05T23:00:00Z");
    private static final DashboardPeriodDetails PERIOD =
            new DashboardPeriodDetails(
                    LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 9, 5));
    private static final MembershipDashboardDetails MEMBERSHIPS =
            new MembershipDashboardDetails(10, 2, 3);
    private static final AccessDashboardDetails ACCESS =
            new AccessDashboardDetails(15, 1);
    private static final DashboardNotificationDetails NOTIFICATIONS =
            new DashboardNotificationDetails(4);

    @Test
    void createsCompleteAdministratorProjection() {
        OperationalDashboardDetails dashboard =
                OperationalDashboardDetails.forAdministrator(
                        GENERATED_AT,
                        PERIOD,
                        MEMBERSHIPS,
                        ACCESS,
                        new PaymentDashboardDetails(
                                8, new BigDecimal("400.00"), "USD"),
                        new EquipmentDashboardDetails(20, 2, 1),
                        new IncidentDashboardDetails(2, 1, 1),
                        new MaintenanceDashboardDetails(3, 1, 1),
                        NOTIFICATIONS);

        assertThat(dashboard.administrativeSectionsIncluded()).isTrue();
        assertThat(dashboard.payments()).isNotNull();
        assertThat(dashboard.notifications().unread()).isEqualTo(4);
    }

    @Test
    void createsLimitedReceptionistProjectionWithNullAdministrativeSections() {
        OperationalDashboardDetails dashboard =
                OperationalDashboardDetails.forReceptionist(
                        GENERATED_AT,
                        PERIOD,
                        MEMBERSHIPS,
                        ACCESS,
                        NOTIFICATIONS);

        assertThat(dashboard.administrativeSectionsIncluded()).isFalse();
        assertThat(dashboard.payments()).isNull();
        assertThat(dashboard.equipment()).isNull();
        assertThat(dashboard.incidents()).isNull();
        assertThat(dashboard.maintenance()).isNull();
        assertThat(dashboard.memberships()).isNotNull();
        assertThat(dashboard.access()).isNotNull();
        assertThat(dashboard.notifications()).isNotNull();
    }

    @Test
    void administratorFactoryRequiresAllAdministrativeSections() {
        assertThatThrownBy(() -> OperationalDashboardDetails.forAdministrator(
                GENERATED_AT,
                PERIOD,
                MEMBERSHIPS,
                ACCESS,
                null,
                new EquipmentDashboardDetails(0, 0, 0),
                new IncidentDashboardDetails(0, 0, 0),
                new MaintenanceDashboardDetails(0, 0, 0),
                NOTIFICATIONS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingSharedSections() {
        assertThatThrownBy(() -> new OperationalDashboardDetails(
                GENERATED_AT,
                PERIOD,
                null,
                ACCESS,
                null,
                null,
                null,
                null,
                NOTIFICATIONS))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
