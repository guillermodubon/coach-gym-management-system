package io.github.guillermodubon.coachgym.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private StaffScopeQuery staffScopeQuery;

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
                staffScopeQuery,
                clock);
    }

    @Test
    void persistedOrganizationAdministratorReceivesCompleteDashboard() {
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
    void rejectsReceptionistBeforeExecutingAnyGlobalDashboardQuery() {
        UUID actorId = actorId();
        when(actor.id()).thenReturn(actorId);
        when(staffScopeQuery.findAuthorizationContext(actorId)).thenReturn(Optional.of(
                new StaffAuthorizationContext(
                        actorId,
                        Set.of(RoleCode.RECEPTIONIST),
                        StaffAccountStatus.ACTIVE,
                        StaffScopeType.BRANCH,
                        Set.of(UUID.randomUUID()))));

        assertThatThrownBy(() -> service.getDashboard(DashboardQuery.defaults(), actor))
                .isInstanceOf(ReportingAccessDeniedException.class);
        verifyNoInteractions(
                periodResolver,
                settingsQuery,
                membershipQuery,
                accessQuery,
                paymentQuery,
                equipmentQuery,
                incidentQuery,
                maintenanceQuery);
        org.mockito.Mockito.verifyNoInteractions(notificationQuery);
    }

    @Test
    void rejectsBranchAdministratorEvenWhenThePersistedRoleIsAdmin() {
        UUID actorId = actorId();
        when(actor.id()).thenReturn(actorId);
        when(staffScopeQuery.findAuthorizationContext(actorId)).thenReturn(Optional.of(
                new StaffAuthorizationContext(
                        actorId,
                        Set.of(RoleCode.ADMIN),
                        StaffAccountStatus.ACTIVE,
                        StaffScopeType.BRANCH,
                        Set.of(UUID.randomUUID()))));

        assertThatThrownBy(() -> service.getDashboard(DashboardQuery.defaults(), actor))
                .isInstanceOf(ReportingAccessDeniedException.class);
        verifyNoInteractions(periodResolver, settingsQuery, membershipQuery,
                accessQuery, paymentQuery, equipmentQuery, incidentQuery,
                maintenanceQuery, notificationQuery);
    }

    private UUID common(UUID actorId) {
        when(actor.id())
                .thenReturn(actorId);
        when(staffScopeQuery.findAuthorizationContext(actorId)).thenReturn(Optional.of(
                new StaffAuthorizationContext(
                        actorId,
                        Set.of(RoleCode.ADMIN),
                        StaffAccountStatus.ACTIVE,
                        StaffScopeType.ORGANIZATION,
                        Set.of())));

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

    private static UUID actorId() {
        return UUID.randomUUID();
    }
}
