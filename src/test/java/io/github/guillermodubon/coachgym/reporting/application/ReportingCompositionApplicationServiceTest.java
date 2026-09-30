package io.github.guillermodubon.coachgym.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.guillermodubon.coachgym.access.AccessDailyTrendPoint;
import io.github.guillermodubon.coachgym.access.AccessOperationalDaySummary;
import io.github.guillermodubon.coachgym.access.AccessReportingQuery;
import io.github.guillermodubon.coachgym.access.AccessReportingSummary;
import io.github.guillermodubon.coachgym.access.AccessReportingUnavailableException;
import io.github.guillermodubon.coachgym.client.ClientReportingQuery;
import io.github.guillermodubon.coachgym.client.ClientReportingSummary;
import io.github.guillermodubon.coachgym.equipment.EquipmentBranchReportingSummary;
import io.github.guillermodubon.coachgym.equipment.EquipmentReportingQuery;
import io.github.guillermodubon.coachgym.equipment.EquipmentReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.IncidentBranchReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportingQuery;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceBranchReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceReportingQuery;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceReportingSummary;
import io.github.guillermodubon.coachgym.membership.MembershipReportingQuery;
import io.github.guillermodubon.coachgym.membership.MembershipReportingSummary;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryBranchReportingSummary;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryReportingQuery;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryReportingSummary;
import io.github.guillermodubon.coachgym.organization.GymBranchReportingQuery;
import io.github.guillermodubon.coachgym.organization.GymBranchStatus;
import io.github.guillermodubon.coachgym.organization.GymBranchSummary;
import io.github.guillermodubon.coachgym.organization.OrganizationDetails;
import io.github.guillermodubon.coachgym.organization.OrganizationIdentityQuery;
import io.github.guillermodubon.coachgym.organization.OrganizationStatus;
import io.github.guillermodubon.coachgym.payment.PaidPaymentTrendPoint;
import io.github.guillermodubon.coachgym.payment.PaymentFinancialMetrics;
import io.github.guillermodubon.coachgym.payment.PaymentReportingQuery;
import io.github.guillermodubon.coachgym.payment.PaymentReportingUnavailableException;
import io.github.guillermodubon.coachgym.payment.PaymentTrendGranularity;
import io.github.guillermodubon.coachgym.reporting.AccessTrendReport;
import io.github.guillermodubon.coachgym.reporting.AdministratorReportingDashboardSummary;
import io.github.guillermodubon.coachgym.reporting.BranchComparisonReport;
import io.github.guillermodubon.coachgym.reporting.BranchComparisonRequest;
import io.github.guillermodubon.coachgym.reporting.BranchReportingSelection;
import io.github.guillermodubon.coachgym.reporting.PaidFinancialTrendReport;
import io.github.guillermodubon.coachgym.reporting.ReceptionistReportingDashboardSummary;
import io.github.guillermodubon.coachgym.reporting.ReportingDashboardRequest;
import io.github.guillermodubon.coachgym.reporting.ReportingGranularity;
import io.github.guillermodubon.coachgym.reporting.ReportingTrendRequest;
import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import io.github.guillermodubon.coachgym.user.ActiveBranchContextResolver;
import io.github.guillermodubon.coachgym.user.ActiveBranchContextUnavailableException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffBranchContext;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class ReportingCompositionApplicationServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_A = UUID.fromString(
            "30000000-0000-0000-0000-000000000011");
    private static final UUID BRANCH_B = UUID.fromString(
            "30000000-0000-0000-0000-000000000012");
    private static final UUID BRANCH_C = UUID.fromString(
            "30000000-0000-0000-0000-000000000013");
    private static final UUID USER_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000021");
    private static final Instant NOW = Instant.parse("2026-09-20T18:00:00Z");
    private static final LocalDate FROM = LocalDate.parse("2026-09-01");
    private static final LocalDate UNTIL = LocalDate.parse("2026-09-21");

    @Mock private StaffScopeQuery staffScopeQuery;
    @Mock private ActiveBranchContextResolver activeBranchContextResolver;
    @Mock private OrganizationIdentityQuery organizationQuery;
    @Mock private GymBranchReportingQuery branchQuery;
    @Mock private PaymentReportingQuery paymentQuery;
    @Mock private ClientReportingQuery clientQuery;
    @Mock private MembershipReportingQuery membershipQuery;
    @Mock private AccessReportingQuery accessQuery;
    @Mock private EquipmentReportingQuery equipmentQuery;
    @Mock private IncidentReportingQuery incidentQuery;
    @Mock private MaintenanceReportingQuery maintenanceQuery;
    @Mock private EmailDeliveryReportingQuery emailDeliveryQuery;

    private final Clock clock = Clock.fixed(NOW, ZoneId.of("America/El_Salvador"));
    private final AuthenticatedActor actor = new AuthenticatedActor(USER_ID, "reporter");
    private ReportingCompositionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ReportingCompositionApplicationService(
                staffScopeQuery,
                activeBranchContextResolver,
                organizationQuery,
                branchQuery,
                paymentQuery,
                clientQuery,
                membershipQuery,
                accessQuery,
                equipmentQuery,
                incidentQuery,
                maintenanceQuery,
                emailDeliveryQuery,
                clock);
        lenient().when(organizationQuery.findCanonical()).thenReturn(Optional.of(organization()));
        lenient().when(branchQuery.findCanonicalBranches(anyCollection(), anyBoolean()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<UUID> requested = List.copyOf((java.util.Collection<UUID>) invocation.getArgument(0));
                    boolean includeInactive = invocation.getArgument(1);
                    return allBranches().stream()
                            .filter(branch -> requested.contains(branch.id()))
                            .filter(branch -> includeInactive || branch.status() == GymBranchStatus.ACTIVE)
                            .toList();
                });
        stubZeroAdministratorMetrics();
    }

    @Test
    void organizationAdministratorGetsCompleteOrganizationSummaryAndExplicitContext() {
        authorize(RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());

        AdministratorReportingDashboardSummary result = (AdministratorReportingDashboardSummary)
                service.dashboard(new ReportingDashboardRequest(null, FROM, UNTIL), actor);

        assertThat(result.context().selection().scope())
                .isEqualTo(io.github.guillermodubon.coachgym.reporting.ReportingScope.ORGANIZATION);
        assertThat(result.context().range().timezone())
                .isEqualTo(ZoneId.of("America/El_Salvador"));
        assertThat(result.context().range().fromInclusive()).isEqualTo(FROM);
        assertThat(result.context().range().toExclusive()).isEqualTo(UNTIL);
        assertThat(result.context().generatedAt()).isEqualTo(NOW);
        assertThat(result.metrics().financial().currencies()).isEmpty();
        assertThat(result.metrics().clients()).isEqualTo(new ClientReportingSummary(0, 0, 0, 0));
        verify(paymentQuery).summarize(eq(ReportingQueryScope.organization()), any());
        verify(branchQuery, never()).findCanonicalBranches(anyCollection(), anyBoolean());
    }

    @Test
    void organizationAdministratorCanRequestAnAuthorizedSingleBranchSummary() {
        authorize(RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());

        AdministratorReportingDashboardSummary result = (AdministratorReportingDashboardSummary)
                service.dashboard(
                        new ReportingDashboardRequest(
                                BranchReportingSelection.singleBranch(BRANCH_A), FROM, UNTIL),
                        actor);

        assertThat(result.context().selection().branchIds()).containsExactly(BRANCH_A);
        assertThat(result.context().range().timezone()).isEqualTo(ZoneId.of("America/El_Salvador"));
        verify(paymentQuery).summarize(
                eq(ReportingQueryScope.branches(List.of(BRANCH_A))), any());
    }

    @Test
    void branchAdministratorCanReadOnlyAnAssignedActiveBranchAndUsesItsTimezone() {
        authorize(RoleCode.ADMIN, StaffScopeType.BRANCH, Set.of(BRANCH_A, BRANCH_B));

        AdministratorReportingDashboardSummary result = (AdministratorReportingDashboardSummary)
                service.dashboard(
                        new ReportingDashboardRequest(
                                BranchReportingSelection.singleBranch(BRANCH_A), FROM, UNTIL),
                        actor);

        assertThat(result.context().selection().branchIds()).containsExactly(BRANCH_A);
        assertThat(result.context().range().timezone())
                .isEqualTo(ZoneId.of("America/El_Salvador"));
        verify(paymentQuery).summarize(
                eq(ReportingQueryScope.branches(List.of(BRANCH_A))), any());
    }

    @Test
    void branchAdministratorCannotRequestOrganizationOrAnotherBranch() {
        authorize(RoleCode.ADMIN, StaffScopeType.BRANCH, Set.of(BRANCH_A));

        assertThatThrownBy(() -> service.dashboard(
                new ReportingDashboardRequest(
                        BranchReportingSelection.organizationWide(), FROM, UNTIL), actor))
                .isInstanceOf(ReportingAccessDeniedException.class);
        assertThatThrownBy(() -> service.dashboard(
                new ReportingDashboardRequest(
                        BranchReportingSelection.singleBranch(BRANCH_C), FROM, UNTIL), actor))
                .isInstanceOf(ReportingAccessDeniedException.class);
        verifyNoInteractions(paymentQuery, clientQuery, membershipQuery, accessQuery,
                equipmentQuery, incidentQuery, maintenanceQuery, emailDeliveryQuery, branchQuery);
    }

    @Test
    void missingBranchAndUnauthorizedBranchUseTheSameSafeDenial() {
        authorize(RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());
        when(branchQuery.findCanonicalBranches(anyCollection(), eq(false))).thenReturn(List.of());

        assertThatThrownBy(() -> service.dashboard(
                new ReportingDashboardRequest(
                        BranchReportingSelection.singleBranch(BRANCH_C), FROM, UNTIL), actor))
                .isInstanceOf(ReportingAccessDeniedException.class)
                .hasMessage("Reporting access is not available for the requested scope.")
                .hasMessageNotContaining(BRANCH_C.toString());
        verifyNoInteractions(paymentQuery, clientQuery, membershipQuery, accessQuery,
                equipmentQuery, incidentQuery, maintenanceQuery, emailDeliveryQuery);
    }

    @Test
    void receptionistDefaultsToActiveBranchAndReceivesOnlyApprovedCounts() throws Exception {
        authorize(RoleCode.RECEPTIONIST, StaffScopeType.BRANCH, Set.of(BRANCH_A));
        when(activeBranchContextResolver.resolve(USER_ID)).thenReturn(activeBranchContext(BRANCH_A));
        when(membershipQuery.summarize(any(), any(), any()))
                .thenReturn(new MembershipReportingSummary(
                        11, 2, 3, 4, 5, 6, 7, 8,
                        List.of(new MembershipReportingSummary.PlanDistribution("PREMIUM", "Private Plan", 11)),
                        List.of()));
        when(accessQuery.summarizeOperationalDay(any(), any(), any()))
                .thenReturn(new AccessOperationalDaySummary(
                        LocalDate.of(2026, 9, 20),
                        ZoneId.of("America/El_Salvador"), 17, 12, 5));

        ReceptionistReportingDashboardSummary result = (ReceptionistReportingDashboardSummary)
                service.dashboard(new ReportingDashboardRequest(null, FROM, UNTIL), actor);
        String json = json(result);

        assertThat(result.context().selection().scope())
                .isEqualTo(io.github.guillermodubon.coachgym.reporting.ReportingScope.ACTIVE_BRANCH);
        assertThat(result.context().selection().branchIds()).containsExactly(BRANCH_A);
        assertThat(result.memberships().activeMemberships()).isEqualTo(11);
        assertThat(result.memberships().frozenMemberships()).isEqualTo(2);
        assertThat(result.memberships().expiringPeriods()).isEqualTo(8);
        assertThat(result.access().allowedAttempts()).isEqualTo(12);
        assertThat(result.access().deniedAttempts()).isEqualTo(5);
        assertThat(json)
                .doesNotContain("financial", "clients", "durableEmail", "equipment",
                        "incidents", "maintenance", "planDistribution", "coverageDistribution",
                        "notifications", "recipient", "token", "providerMessageId");
        verifyNoInteractions(paymentQuery, clientQuery, equipmentQuery, incidentQuery,
                maintenanceQuery, emailDeliveryQuery);
    }

    @Test
    void branchComparisonIsBoundedAuthorizedAndDeterministicallyOrdered() {
        authorize(RoleCode.ADMIN, StaffScopeType.BRANCH, Set.of(BRANCH_A, BRANCH_B));
        configureBranchSummaries();

        BranchComparisonReport result = service.compareBranches(
                new BranchComparisonRequest(
                        BranchReportingSelection.authorizedBranches(List.of(BRANCH_B, BRANCH_A)),
                        FROM,
                        UNTIL,
                        false),
                actor);

        assertThat(result.branches())
                .extracting(row -> row.branch().branchCode())
                .containsExactly("NORTH", "SOUTH");
        assertThat(result.context().range().timezone())
                .isEqualTo(ZoneId.of("America/El_Salvador"));
        verify(branchQuery).findCanonicalBranches(
                eq(List.of(BRANCH_A, BRANCH_B)), eq(false));
        verify(paymentQuery).summarize(
                eq(ReportingQueryScope.branches(List.of(BRANCH_A))), any());
        verify(paymentQuery).summarize(
                eq(ReportingQueryScope.branches(List.of(BRANCH_B))), any());
    }

    @Test
    void missingComparisonRowsBecomeStableZeroes() {
        authorize(RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());
        when(branchQuery.findCanonicalBranches(anyCollection(), eq(false)))
                .thenReturn(List.of(branch(BRANCH_A, "NORTH", GymBranchStatus.ACTIVE),
                        branch(BRANCH_B, "SOUTH", GymBranchStatus.ACTIVE)));
        when(equipmentQuery.summarizeByBranch(any())).thenReturn(List.of());
        when(incidentQuery.summarizeByBranch(any(), any())).thenReturn(List.of());
        when(maintenanceQuery.summarizeByBranch(any(), any())).thenReturn(List.of());
        when(emailDeliveryQuery.summarizeByBranch(any(), any())).thenReturn(List.of());

        BranchComparisonReport result = service.compareBranches(
                new BranchComparisonRequest(
                        BranchReportingSelection.authorizedBranches(List.of(BRANCH_A, BRANCH_B)),
                        FROM,
                        UNTIL,
                        false),
                actor);

        assertThat(result.branches()).hasSize(2);
        assertThat(result.branches()).allSatisfy(row -> {
            assertThat(row.metrics().financial().currencies()).isEmpty();
            assertThat(row.metrics().equipment().totalEquipment()).isZero();
            assertThat(row.metrics().incidents().totalIncidents()).isZero();
            assertThat(row.metrics().maintenance().totalMaintenance()).isZero();
            assertThat(row.metrics().durableEmail().totalDeliveries()).isZero();
            assertThat(row.metrics().access().totalAttempts()).isZero();
        });
    }

    @Test
    void branchComparisonRejectsUnassignedBranchBeforeMetadataOrMetrics() {
        authorize(RoleCode.ADMIN, StaffScopeType.BRANCH, Set.of(BRANCH_A));

        assertThatThrownBy(() -> service.compareBranches(
                new BranchComparisonRequest(
                        BranchReportingSelection.authorizedBranches(List.of(BRANCH_A, BRANCH_C)),
                        FROM,
                        UNTIL,
                        false),
                actor))
                .isInstanceOf(ReportingAccessDeniedException.class)
                .hasMessageNotContaining(BRANCH_C.toString());
        verifyNoInteractions(branchQuery, paymentQuery, clientQuery, membershipQuery,
                accessQuery, equipmentQuery, incidentQuery, maintenanceQuery, emailDeliveryQuery);
    }

    @Test
    void onlyOrganizationAdministratorMayExplicitlyIncludeInactiveBranches() {
        authorize(RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());
        when(branchQuery.findCanonicalBranches(anyCollection(), eq(true)))
                .thenReturn(List.of(
                        branch(BRANCH_A, "NORTH", GymBranchStatus.ACTIVE),
                        branch(BRANCH_B, "SOUTH", GymBranchStatus.INACTIVE)));
        configureBranchSummaries();

        BranchComparisonReport result = service.compareBranches(
                new BranchComparisonRequest(
                        BranchReportingSelection.authorizedBranches(List.of(BRANCH_A, BRANCH_B)),
                        FROM,
                        UNTIL,
                        true),
                actor);

        assertThat(result.branches()).extracting(row -> row.branch().active())
                .containsExactly(true, false);
        verify(branchQuery).findCanonicalBranches(
                eq(List.of(BRANCH_A, BRANCH_B)), eq(true));
    }

    @Test
    void branchAdministratorCannotRequestHistoricalInactiveBranchComparison() {
        authorize(RoleCode.ADMIN, StaffScopeType.BRANCH, Set.of(BRANCH_A, BRANCH_B));

        assertThatThrownBy(() -> service.compareBranches(
                new BranchComparisonRequest(
                        BranchReportingSelection.authorizedBranches(List.of(BRANCH_A, BRANCH_B)),
                        FROM,
                        UNTIL,
                        true),
                actor))
                .isInstanceOf(ReportingAccessDeniedException.class);
        verifyNoInteractions(branchQuery, paymentQuery, clientQuery, membershipQuery,
                accessQuery, equipmentQuery, incidentQuery, maintenanceQuery, emailDeliveryQuery);
    }

    @Test
    void receptionistCannotUseBranchComparisonEvenForAnAssignedBranchSet() {
        authorize(RoleCode.RECEPTIONIST, StaffScopeType.BRANCH, Set.of(BRANCH_A, BRANCH_B));

        assertThatThrownBy(() -> service.compareBranches(
                new BranchComparisonRequest(
                        BranchReportingSelection.authorizedBranches(List.of(BRANCH_A, BRANCH_B)),
                        FROM,
                        UNTIL,
                        false),
                actor))
                .isInstanceOf(ReportingAccessDeniedException.class);
        verifyNoInteractions(branchQuery, paymentQuery, clientQuery, membershipQuery,
                accessQuery, equipmentQuery, incidentQuery, maintenanceQuery, emailDeliveryQuery);
    }

    @Test
    void comparisonBranchLimitIsRejectedBeforeAnyBranchLookup() {
        authorize(RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());
        List<UUID> branchIds = java.util.stream.IntStream.rangeClosed(1, 21)
                .mapToObj(index -> new UUID(0, 300_000L + index))
                .toList();

        assertThatThrownBy(() -> service.compareBranches(
                new BranchComparisonRequest(
                        BranchReportingSelection.authorizedBranches(branchIds),
                        FROM,
                        UNTIL,
                        false),
                actor))
                .isInstanceOf(ReportingValidationException.class)
                .hasMessage("Branch comparison exceeds the maximum branch count.");
        verifyNoInteractions(branchQuery, paymentQuery, clientQuery, membershipQuery,
                accessQuery, equipmentQuery, incidentQuery, maintenanceQuery, emailDeliveryQuery);
    }

    @Test
    void focusedFinancialTrendUsesTheRequestedBoundedGranularity() {
        authorize(RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());
        PaidPaymentTrendPoint point = new PaidPaymentTrendPoint(
                LocalDate.of(2026, 8, 31), "USD", 2, new BigDecimal("40.00"));
        when(paymentQuery.paidTrend(any(), any(), eq(PaymentTrendGranularity.WEEKLY)))
                .thenReturn(List.of(point));

        PaidFinancialTrendReport result = service.paidTrend(
                new ReportingTrendRequest(
                        BranchReportingSelection.organizationWide(),
                        FROM,
                        UNTIL,
                        ReportingGranularity.WEEKLY),
                actor);

        assertThat(result.points()).containsExactly(point);
        assertThat(result.context().range().timezone())
                .isEqualTo(ZoneId.of("America/El_Salvador"));
    }

    @Test
    void accessTrendRejectsUnsupportedGranularityWithoutRunningItsQuery() {
        authorize(RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());

        assertThatThrownBy(() -> service.accessTrend(
                new ReportingTrendRequest(
                        BranchReportingSelection.organizationWide(),
                        FROM,
                        UNTIL,
                        ReportingGranularity.WEEKLY),
                actor))
                .isInstanceOf(ReportingValidationException.class)
                .hasMessage("Access trends support daily granularity only.");
        verifyNoInteractions(accessQuery);
    }

    @Test
    void rangeAboveAdrBoundIsRejectedBeforeMetricQueries() {
        authorize(RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());

        assertThatThrownBy(() -> service.dashboard(
                new ReportingDashboardRequest(
                        null,
                        LocalDate.parse("2025-08-01"),
                        LocalDate.parse("2026-09-02")),
                actor))
                .isInstanceOf(ReportingValidationException.class)
                .hasMessage("Reporting date range is invalid.");
        verifyNoInteractions(paymentQuery, clientQuery, membershipQuery, accessQuery,
                equipmentQuery, incidentQuery, maintenanceQuery, emailDeliveryQuery);
    }

    @Test
    void sourceFailureIsTranslatedToSafeReportingError() {
        authorize(RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());
        when(paymentQuery.summarize(any(), any()))
                .thenThrow(new PaymentReportingUnavailableException(
                        new IllegalStateException("private SQL, provider token, database host")));

        assertThatThrownBy(() -> service.dashboard(
                new ReportingDashboardRequest(null, FROM, UNTIL), actor))
                .isInstanceOf(ReportingDataAccessException.class)
                .hasMessage("Reporting data could not be read.")
                .satisfies(exception -> assertThat(exception.getMessage())
                        .doesNotContain("private SQL", "provider token", "database host"));
    }

    @Test
    void serializedAdministratorProjectionHasNoSensitiveOrNotificationFields() throws Exception {
        authorize(RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());
        String json = json(service.dashboard(
                new ReportingDashboardRequest(null, FROM, UNTIL), actor));

        assertThat(json)
                .contains("financial", "memberships", "access", "durableEmail", "generatedAt")
                .doesNotContain("emailAddress", "recipient", "providerMessageId", "token",
                        "payload", "mime", "attachment", "clientName", "notification");
    }

    @Test
    void everyCompositionEntryPointIsRoleProtectedAndReadOnly() throws Exception {
        assertThat(ReportingCompositionApplicationService.class
                .getAnnotation(Transactional.class).readOnly()).isTrue();
        for (String methodName : List.of("dashboard", "compareBranches", "paidTrend", "accessTrend")) {
            java.lang.reflect.Method method = java.util.Arrays.stream(
                            ReportingCompositionApplicationService.class.getMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            assertThat(method.getAnnotation(PreAuthorize.class).value())
                    .isEqualTo("hasAnyRole('ADMIN', 'RECEPTIONIST')");
        }
    }

    @Test
    void activeBranchUnavailableFailsClosedWithoutConsultingMetricSources() {
        authorize(RoleCode.RECEPTIONIST, StaffScopeType.BRANCH, Set.of(BRANCH_A));
        when(activeBranchContextResolver.resolve(USER_ID))
                .thenThrow(new ActiveBranchContextUnavailableException());

        assertThatThrownBy(() -> service.dashboard(
                new ReportingDashboardRequest(null, FROM, UNTIL), actor))
                .isInstanceOf(ReportingAccessDeniedException.class);
        verifyNoInteractions(paymentQuery, clientQuery, membershipQuery, accessQuery,
                equipmentQuery, incidentQuery, maintenanceQuery, emailDeliveryQuery);
    }

    private void authorize(RoleCode role, StaffScopeType scope, Set<UUID> branchIds) {
        when(staffScopeQuery.findAuthorizationContext(USER_ID)).thenReturn(Optional.of(
                new StaffAuthorizationContext(
                        USER_ID, Set.of(role), StaffAccountStatus.ACTIVE, scope, branchIds)));
    }

    private void stubZeroAdministratorMetrics() {
        lenient().when(paymentQuery.summarize(any(), any()))
                .thenReturn(new PaymentFinancialMetrics(List.of()));
        lenient().when(clientQuery.summarize(any(), any()))
                .thenReturn(new ClientReportingSummary(0, 0, 0, 0));
        lenient().when(membershipQuery.summarize(any(), any(), any()))
                .thenReturn(emptyMemberships());
        lenient().when(accessQuery.summarize(any(), any())).thenReturn(AccessReportingSummary.empty());
        lenient().when(equipmentQuery.summarize(any())).thenReturn(EquipmentReportingSummary.empty());
        lenient().when(incidentQuery.summarize(any(), any())).thenReturn(IncidentReportingSummary.empty());
        lenient().when(maintenanceQuery.summarize(any(), any()))
                .thenReturn(MaintenanceReportingSummary.empty());
        lenient().when(emailDeliveryQuery.summarize(any(), any()))
                .thenReturn(EmailDeliveryReportingSummary.empty());
    }

    private void configureBranchSummaries() {
        when(equipmentQuery.summarizeByBranch(any())).thenReturn(List.of(
                new EquipmentBranchReportingSummary(BRANCH_A, EquipmentReportingSummary.empty()),
                new EquipmentBranchReportingSummary(BRANCH_B, EquipmentReportingSummary.empty())));
        when(incidentQuery.summarizeByBranch(any(), any())).thenReturn(List.of(
                new IncidentBranchReportingSummary(BRANCH_A, IncidentReportingSummary.empty()),
                new IncidentBranchReportingSummary(BRANCH_B, IncidentReportingSummary.empty())));
        when(maintenanceQuery.summarizeByBranch(any(), any())).thenReturn(List.of(
                new MaintenanceBranchReportingSummary(BRANCH_A, MaintenanceReportingSummary.empty()),
                new MaintenanceBranchReportingSummary(BRANCH_B, MaintenanceReportingSummary.empty())));
        when(emailDeliveryQuery.summarizeByBranch(any(), any())).thenReturn(List.of(
                new EmailDeliveryBranchReportingSummary(BRANCH_A, EmailDeliveryReportingSummary.empty()),
                new EmailDeliveryBranchReportingSummary(BRANCH_B, EmailDeliveryReportingSummary.empty())));
    }

    private StaffBranchContext activeBranchContext(UUID branchId) {
        GymBranchSummary selected = allBranches().stream()
                .filter(branch -> branch.id().equals(branchId))
                .findFirst()
                .orElseThrow();
        return new StaffBranchContext(
                ORGANIZATION_ID,
                StaffScopeType.BRANCH,
                branchId,
                List.of(new AuthorizedBranchSummary(
                        selected.id(),
                        selected.organizationId(),
                        selected.code(),
                        selected.name(),
                        selected.timezone(),
                        selected.initialBranch())));
    }

    private static OrganizationDetails organization() {
        return new OrganizationDetails(
                ORGANIZATION_ID,
                "COACH_GYM",
                "Coach Gym Legal",
                "Coach Gym",
                "support@example.test",
                "+50370000000",
                "America/El_Salvador",
                "USD",
                OrganizationStatus.ACTIVE,
                NOW.minusSeconds(3_600),
                NOW,
                0);
    }

    private static List<GymBranchSummary> allBranches() {
        return List.of(
                branch(BRANCH_A, "NORTH", GymBranchStatus.ACTIVE),
                branch(BRANCH_B, "SOUTH", GymBranchStatus.ACTIVE),
                branch(BRANCH_C, "WEST", GymBranchStatus.INACTIVE));
    }

    private static GymBranchSummary branch(
            UUID id, String code, GymBranchStatus status) {
        return new GymBranchSummary(
                id,
                ORGANIZATION_ID,
                code,
                code + " Gym",
                "America/El_Salvador",
                status,
                false);
    }

    private static MembershipReportingSummary emptyMemberships() {
        return new MembershipReportingSummary(0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
    }

    private static String json(Object value) throws Exception {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .writeValueAsString(value);
    }
}
