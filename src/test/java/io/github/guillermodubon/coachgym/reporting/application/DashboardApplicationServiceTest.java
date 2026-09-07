package io.github.guillermodubon.coachgym.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.reporting.AccessDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.DashboardNotificationDetails;
import io.github.guillermodubon.coachgym.reporting.DashboardPeriodDetails;
import io.github.guillermodubon.coachgym.reporting.EquipmentDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.IncidentDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.MaintenanceDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.MembershipDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.OperationalDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.PaymentDashboardDetails;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class DashboardApplicationServiceTest {

    @Mock
    private DashboardPeriodResolver periodResolver;

    @Mock
    private DashboardSettingsQuery settingsQuery;

    @Mock
    private MembershipDashboardQuery membershipQuery;

    @Mock
    private AccessDashboardQuery accessQuery;

    @Mock
    private PaymentDashboardQuery paymentQuery;

    @Mock
    private EquipmentDashboardQuery equipmentQuery;

    @Mock
    private IncidentDashboardQuery incidentQuery;

    @Mock
    private MaintenanceDashboardQuery maintenanceQuery;

    @Mock
    private DashboardNotificationQuery notificationQuery;

    @Mock
    private AuthenticatedActor actor;

    private Clock clock;
    private DashboardPeriod period;
    private DashboardApplicationService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
                Instant.parse("2026-09-05T18:00:00Z"),
                ZoneId.of("America/El_Salvador"));

        period = new DashboardPeriod(
                new DashboardPeriodDetails(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 5)),
                LocalDate.of(2026, 9, 5),
                clock.getZone(),
                Instant.parse("2026-09-01T06:00:00Z"),
                Instant.parse("2026-09-06T06:00:00Z"));

        service = new DashboardApplicationService(
                periodResolver,
                settingsQuery,
                membershipQuery,
                accessQuery,
                paymentQuery,
                equipmentQuery,
                incidentQuery,
                maintenanceQuery,
                notificationQuery,
                clock);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administratorReceivesCompleteDashboard() {
        authenticate("ROLE_ADMIN");

        UUID actorId = common(actorId());

        when(paymentQuery.summarize(period, "USD"))
                .thenReturn(
                        new PaymentDashboardDetails(
                                4,
                                new BigDecimal("200.00"),
                                "USD"));

        when(equipmentQuery.summarize())
                .thenReturn(
                        new EquipmentDashboardDetails(
                                10,
                                2,
                                1));

        when(incidentQuery.summarize())
                .thenReturn(
                        new IncidentDashboardDetails(
                                2,
                                1,
                                1));

        when(maintenanceQuery.summarize(
                period.operationalDate()))
                .thenReturn(
                        new MaintenanceDashboardDetails(
                                3,
                                1,
                                1));

        OperationalDashboardDetails result =
                service.getDashboard(
                        DashboardQuery.defaults(),
                        actor);

        assertThat(result.administrativeSectionsIncluded())
                .isTrue();

        assertThat(result.generatedAt())
                .isEqualTo(clock.instant());

        assertThat(result.payments())
                .isNotNull();

        assertThat(result.equipment())
                .isNotNull();

        assertThat(result.incidents())
                .isNotNull();

        assertThat(result.maintenance())
                .isNotNull();

        verify(notificationQuery)
                .summarize(actorId);
    }

    @Test
    void receptionistReceivesLimitedDashboardWithoutRestrictedQueries() {
        authenticate("ROLE_RECEPTIONIST");

        UUID actorId = common(actorId());

        OperationalDashboardDetails result =
                service.getDashboard(
                        DashboardQuery.defaults(),
                        actor);

        assertThat(result.administrativeSectionsIncluded())
                .isFalse();

        assertThat(result.payments())
                .isNull();

        assertThat(result.equipment())
                .isNull();

        assertThat(result.incidents())
                .isNull();

        assertThat(result.maintenance())
                .isNull();

        assertThat(result.memberships())
                .isNotNull();

        assertThat(result.access())
                .isNotNull();

        assertThat(result.notifications())
                .isNotNull();

        verify(notificationQuery)
                .summarize(actorId);

        verifyNoInteractions(
                paymentQuery,
                equipmentQuery,
                incidentQuery,
                maintenanceQuery);
    }

    @Test
    void administratorRoleTakesPrecedenceForMultiRoleActor() {
        authenticate(
                "ROLE_ADMIN",
                "ROLE_RECEPTIONIST");

        common(actorId());

        when(paymentQuery.summarize(period, "USD"))
                .thenReturn(
                        new PaymentDashboardDetails(
                                0,
                                BigDecimal.ZERO,
                                "USD"));

        when(equipmentQuery.summarize())
                .thenReturn(
                        new EquipmentDashboardDetails(
                                0,
                                0,
                                0));

        when(incidentQuery.summarize())
                .thenReturn(
                        new IncidentDashboardDetails(
                                0,
                                0,
                                0));

        when(maintenanceQuery.summarize(
                period.operationalDate()))
                .thenReturn(
                        new MaintenanceDashboardDetails(
                                0,
                                0,
                                0));

        OperationalDashboardDetails result =
                service.getDashboard(
                        null,
                        actor);

        assertThat(result.administrativeSectionsIncluded())
                .isTrue();

        assertThat(result.payments())
                .isNotNull();

        assertThat(result.equipment())
                .isNotNull();

        assertThat(result.incidents())
                .isNotNull();

        assertThat(result.maintenance())
                .isNotNull();
    }

    private UUID common(UUID actorId) {
        when(actor.id())
                .thenReturn(actorId);

        when(periodResolver.resolve(
                nullable(DashboardQuery.class)))
                .thenReturn(period);

        when(settingsQuery.load())
                .thenReturn(
                        new DashboardSettings(
                                7,
                                "USD"));

        when(membershipQuery.summarize(
                period,
                7))
                .thenReturn(
                        new MembershipDashboardDetails(
                                12,
                                2,
                                3));

        when(accessQuery.summarizeToday(period))
                .thenReturn(
                        new AccessDashboardDetails(
                                20,
                                1));

        when(notificationQuery.summarize(actorId))
                .thenReturn(
                        new DashboardNotificationDetails(
                                5));

        return actorId;
    }

    private static void authenticate(String... authorities) {
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(
                        "dashboard-user",
                        null,
                        authorities);

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    private static UUID actorId() {
        return UUID.randomUUID();
    }
}