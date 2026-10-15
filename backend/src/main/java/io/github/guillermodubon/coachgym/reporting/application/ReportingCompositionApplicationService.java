package io.github.guillermodubon.coachgym.reporting.application;

import io.github.guillermodubon.coachgym.access.AccessDailyTrendPoint;
import io.github.guillermodubon.coachgym.access.AccessOperationalDaySummary;
import io.github.guillermodubon.coachgym.access.AccessReportingQuery;
import io.github.guillermodubon.coachgym.access.AccessReportingSummary;
import io.github.guillermodubon.coachgym.access.AccessReportingUnavailableException;
import io.github.guillermodubon.coachgym.client.ClientReportingQuery;
import io.github.guillermodubon.coachgym.client.ClientReportingSummary;
import io.github.guillermodubon.coachgym.client.ClientReportingUnavailableException;
import io.github.guillermodubon.coachgym.equipment.EquipmentBranchReportingSummary;
import io.github.guillermodubon.coachgym.equipment.EquipmentReportingQuery;
import io.github.guillermodubon.coachgym.equipment.EquipmentReportingSummary;
import io.github.guillermodubon.coachgym.equipment.EquipmentReportingUnavailableException;
import io.github.guillermodubon.coachgym.maintenance.IncidentBranchReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportingQuery;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportingUnavailableException;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceBranchReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceReportingQuery;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceReportingUnavailableException;
import io.github.guillermodubon.coachgym.membership.MembershipReportingQuery;
import io.github.guillermodubon.coachgym.membership.MembershipReportingSummary;
import io.github.guillermodubon.coachgym.membership.MembershipReportingUnavailableException;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryBranchReportingSummary;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryReportingQuery;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryReportingSummary;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryReportingUnavailableException;
import io.github.guillermodubon.coachgym.organization.GymBranchReportingQuery;
import io.github.guillermodubon.coachgym.organization.GymBranchReportingUnavailableException;
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
import io.github.guillermodubon.coachgym.reporting.AdministratorReportingMetrics;
import io.github.guillermodubon.coachgym.reporting.BranchComparisonReport;
import io.github.guillermodubon.coachgym.reporting.BranchComparisonRequest;
import io.github.guillermodubon.coachgym.reporting.BranchComparisonRow;
import io.github.guillermodubon.coachgym.reporting.BranchReportingSelection;
import io.github.guillermodubon.coachgym.reporting.PaidFinancialTrendReport;
import io.github.guillermodubon.coachgym.reporting.ReceptionistAccessSummary;
import io.github.guillermodubon.coachgym.reporting.ReceptionistMembershipSummary;
import io.github.guillermodubon.coachgym.reporting.ReceptionistReportingDashboardSummary;
import io.github.guillermodubon.coachgym.reporting.ReportingAccessPolicy;
import io.github.guillermodubon.coachgym.reporting.ReportingBranchIdentity;
import io.github.guillermodubon.coachgym.reporting.ReportingContext;
import io.github.guillermodubon.coachgym.reporting.ReportingDashboardRequest;
import io.github.guillermodubon.coachgym.reporting.ReportingDashboardSummary;
import io.github.guillermodubon.coachgym.reporting.ReportingGranularity;
import io.github.guillermodubon.coachgym.reporting.ReportingMetricGroup;
import io.github.guillermodubon.coachgym.reporting.ReportingRange;
import io.github.guillermodubon.coachgym.reporting.ReportingRangePolicy;
import io.github.guillermodubon.coachgym.reporting.ReportingScope;
import io.github.guillermodubon.coachgym.reporting.ReportingTrendRequest;
import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import io.github.guillermodubon.coachgym.user.ActiveBranchContextResolver;
import io.github.guillermodubon.coachgym.user.ActiveBranchContextUnavailableException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffBranchContext;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes bounded source-owned projections after resolving authorization,
 * branch metadata, and the ADR-defined reporting timezone.
 */
@Service
@Transactional(readOnly = true)
public class ReportingCompositionApplicationService {

    private final StaffScopeQuery staffScopeQuery;
    private final ActiveBranchContextResolver activeBranchContextResolver;
    private final OrganizationIdentityQuery organizationQuery;
    private final GymBranchReportingQuery branchQuery;
    private final PaymentReportingQuery paymentQuery;
    private final ClientReportingQuery clientQuery;
    private final MembershipReportingQuery membershipQuery;
    private final AccessReportingQuery accessQuery;
    private final EquipmentReportingQuery equipmentQuery;
    private final IncidentReportingQuery incidentQuery;
    private final MaintenanceReportingQuery maintenanceQuery;
    private final EmailDeliveryReportingQuery emailDeliveryQuery;
    private final Clock clock;
    private final ReportingRangePolicy rangePolicy = ReportingRangePolicy.defaults();

    public ReportingCompositionApplicationService(
            StaffScopeQuery staffScopeQuery,
            ActiveBranchContextResolver activeBranchContextResolver,
            OrganizationIdentityQuery organizationQuery,
            GymBranchReportingQuery branchQuery,
            PaymentReportingQuery paymentQuery,
            ClientReportingQuery clientQuery,
            MembershipReportingQuery membershipQuery,
            AccessReportingQuery accessQuery,
            EquipmentReportingQuery equipmentQuery,
            IncidentReportingQuery incidentQuery,
            MaintenanceReportingQuery maintenanceQuery,
            EmailDeliveryReportingQuery emailDeliveryQuery,
            Clock clock) {
        this.staffScopeQuery = Objects.requireNonNull(staffScopeQuery);
        this.activeBranchContextResolver = Objects.requireNonNull(activeBranchContextResolver);
        this.organizationQuery = Objects.requireNonNull(organizationQuery);
        this.branchQuery = Objects.requireNonNull(branchQuery);
        this.paymentQuery = Objects.requireNonNull(paymentQuery);
        this.clientQuery = Objects.requireNonNull(clientQuery);
        this.membershipQuery = Objects.requireNonNull(membershipQuery);
        this.accessQuery = Objects.requireNonNull(accessQuery);
        this.equipmentQuery = Objects.requireNonNull(equipmentQuery);
        this.incidentQuery = Objects.requireNonNull(incidentQuery);
        this.maintenanceQuery = Objects.requireNonNull(maintenanceQuery);
        this.emailDeliveryQuery = Objects.requireNonNull(emailDeliveryQuery);
        this.clock = Objects.requireNonNull(clock);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ReportingDashboardSummary dashboard(
            ReportingDashboardRequest request,
            AuthenticatedActor actor) {
        Objects.requireNonNull(request, "Dashboard request is required.");
        StaffAuthorizationContext authorization = authorization(actor);
        ResolvedReport report = resolve(
                authorization,
                request.selection(),
                request.fromInclusive(),
                request.toExclusive(),
                false,
                false,
                ReportingGranularity.DAILY);

        if (authorization.receptionist()) {
            MembershipReportingSummary memberships = read(() -> membershipQuery.summarize(
                    report.queryScope(), report.queryWindow(), report.operationalDate()));
            AccessOperationalDaySummary access = read(() -> accessQuery.summarizeOperationalDay(
                    report.queryScope(), report.operationalDate(),
                    report.context().range().timezone()));
            return new ReceptionistReportingDashboardSummary(
                    report.context(),
                    new ReceptionistMembershipSummary(
                            memberships.activeMemberships(),
                            memberships.frozenMemberships(),
                            memberships.expiringPeriods()),
                    new ReceptionistAccessSummary(
                            access.day(),
                            access.timezone(),
                            access.allowedAttempts(),
                            access.deniedAttempts()));
        }

        return new AdministratorReportingDashboardSummary(
                report.context(), administratorMetrics(report));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public BranchComparisonReport compareBranches(
            BranchComparisonRequest request,
            AuthenticatedActor actor) {
        Objects.requireNonNull(request, "Branch comparison request is required.");
        StaffAuthorizationContext authorization = authorization(actor);
        if (authorization.receptionist()) {
            throw new ReportingAccessDeniedException();
        }
        if (request.selection().branchIds().size() > BranchComparisonReport.MAXIMUM_BRANCHES) {
            throw new ReportingValidationException("Branch comparison exceeds the maximum branch count.");
        }
        ResolvedReport report = resolve(
                authorization,
                request.selection(),
                request.fromInclusive(),
                request.toExclusive(),
                true,
                request.includeInactiveBranches(),
                ReportingGranularity.DAILY);
        List<UUID> branchIds = report.context().selection().branchIds();
        ReportingQueryScope branchScope = ReportingQueryScope.branches(branchIds);
        Set<UUID> expectedIds = Set.copyOf(branchIds);

        Map<UUID, EquipmentReportingSummary> equipment = indexByBranch(
                read(() -> equipmentQuery.summarizeByBranch(branchScope)),
                EquipmentBranchReportingSummary::branchId,
                EquipmentBranchReportingSummary::metrics,
                expectedIds);
        Map<UUID, IncidentReportingSummary> incidents = indexByBranch(
                read(() -> incidentQuery.summarizeByBranch(branchScope, report.queryWindow())),
                IncidentBranchReportingSummary::branchId,
                IncidentBranchReportingSummary::metrics,
                expectedIds);
        Map<UUID, MaintenanceReportingSummary> maintenance = indexByBranch(
                read(() -> maintenanceQuery.summarizeByBranch(
                        branchScope, report.operationalDate())),
                MaintenanceBranchReportingSummary::branchId,
                MaintenanceBranchReportingSummary::metrics,
                expectedIds);
        Map<UUID, EmailDeliveryReportingSummary> durableEmail = indexByBranch(
                read(() -> emailDeliveryQuery.summarizeByBranch(
                        branchScope, report.queryWindow())),
                EmailDeliveryBranchReportingSummary::branchId,
                EmailDeliveryBranchReportingSummary::summary,
                expectedIds);

        List<BranchComparisonRow> rows = report.branches().stream()
                .map(branch -> {
                    UUID branchId = branch.id();
                    ReportingQueryScope singleBranch = ReportingQueryScope.branches(List.of(branchId));
                    PaymentFinancialMetrics financial = read(() -> paymentQuery.summarize(
                            singleBranch, report.queryWindow()));
                    ClientReportingSummary clients = read(() -> clientQuery.summarize(
                            singleBranch, report.queryWindow()));
                    MembershipReportingSummary memberships = read(() -> membershipQuery.summarize(
                            singleBranch, report.queryWindow(), report.operationalDate()));
                    AccessReportingSummary access = read(() -> accessQuery.summarize(
                            singleBranch, report.queryWindow()));
                    AdministratorReportingMetrics metrics = new AdministratorReportingMetrics(
                            financial,
                            clients,
                            memberships,
                            access,
                            equipment.getOrDefault(branchId, EquipmentReportingSummary.empty()),
                            incidents.getOrDefault(branchId, IncidentReportingSummary.empty()),
                            maintenance.getOrDefault(branchId, MaintenanceReportingSummary.empty()),
                            durableEmail.getOrDefault(branchId, EmailDeliveryReportingSummary.empty()));
                    return new BranchComparisonRow(
                            new ReportingBranchIdentity(
                                    branch.id(),
                                    branch.code(),
                                    branch.name(),
                                    branch.status() == GymBranchStatus.ACTIVE),
                            metrics);
                })
                .toList();
        return new BranchComparisonReport(report.context(), rows);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PaidFinancialTrendReport paidTrend(
            ReportingTrendRequest request,
            AuthenticatedActor actor) {
        Objects.requireNonNull(request, "Financial trend request is required.");
        StaffAuthorizationContext authorization = authorization(actor);
        ResolvedReport report = resolve(
                authorization,
                request.selection(),
                request.fromInclusive(),
                request.toExclusive(),
                false,
                false,
                request.granularity());
        requireAllowed(authorization, ReportingMetricGroup.FINANCIAL_TREND, report.context().selection());
        List<PaidPaymentTrendPoint> points = read(() -> paymentQuery.paidTrend(
                report.queryScope(),
                report.queryWindow(),
                PaymentTrendGranularity.valueOf(request.granularity().name())));
        return new PaidFinancialTrendReport(report.context(), request.granularity(), points);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessTrendReport accessTrend(
            ReportingTrendRequest request,
            AuthenticatedActor actor) {
        Objects.requireNonNull(request, "Access trend request is required.");
        StaffAuthorizationContext authorization = authorization(actor);
        ResolvedReport report = resolve(
                authorization,
                request.selection(),
                request.fromInclusive(),
                request.toExclusive(),
                false,
                false,
                request.granularity());
        requireAllowed(authorization, ReportingMetricGroup.ACCESS_TREND, report.context().selection());
        if (request.granularity() != ReportingGranularity.DAILY) {
            throw new ReportingValidationException(
                    "Access trends support daily granularity only.");
        }
        List<AccessDailyTrendPoint> points = read(() -> accessQuery.dailyTrend(
                report.queryScope(), report.queryWindow()));
        return new AccessTrendReport(report.context(), request.granularity(), points);
    }

    private AdministratorReportingMetrics administratorMetrics(ResolvedReport report) {
        return new AdministratorReportingMetrics(
                read(() -> paymentQuery.summarize(report.queryScope(), report.queryWindow())),
                read(() -> clientQuery.summarize(report.queryScope(), report.queryWindow())),
                read(() -> membershipQuery.summarize(
                        report.queryScope(), report.queryWindow(), report.operationalDate())),
                read(() -> accessQuery.summarize(report.queryScope(), report.queryWindow())),
                read(() -> equipmentQuery.summarize(report.queryScope())),
                read(() -> incidentQuery.summarize(report.queryScope(), report.queryWindow())),
                read(() -> maintenanceQuery.summarize(
                        report.queryScope(), report.operationalDate())),
                read(() -> emailDeliveryQuery.summarize(report.queryScope(), report.queryWindow())));
    }

    private ResolvedReport resolve(
            StaffAuthorizationContext authorization,
            BranchReportingSelection requestedSelection,
            LocalDate fromInclusive,
            LocalDate toExclusive,
            boolean comparison,
            boolean includeInactive,
            ReportingGranularity granularity) {
        BranchReportingSelection selection = requestedSelection == null
                ? defaultSelection(authorization)
                : requestedSelection;
        if (selection.scope() == ReportingScope.AUTHORIZED_BRANCH_SET
                && selection.branchIds().size() > BranchComparisonReport.MAXIMUM_BRANCHES) {
            throw new ReportingValidationException("Reporting selection exceeds the maximum branch count.");
        }
        if (includeInactive && (!comparison || !authorization.organizationAdmin()
                || selection.scope() != ReportingScope.AUTHORIZED_BRANCH_SET)) {
            throw new ReportingAccessDeniedException();
        }
        for (ReportingMetricGroup metric : ReportingMetricGroup.values()) {
            if (authorization.receptionist()
                    && metric != ReportingMetricGroup.MEMBERSHIP_SUMMARY
                    && metric != ReportingMetricGroup.ACCESS_SUMMARY) {
                continue;
            }
            requireAllowed(authorization, metric, selection);
        }

        if (selection.scope() == ReportingScope.ACTIVE_BRANCH) {
            StaffBranchContext branchContext = activeContext(authorization.userId());
            UUID activeBranchId = branchContext.activeBranchId();
            if (activeBranchId == null || !activeBranchId.equals(selection.branchIds().getFirst())) {
                throw new ReportingAccessDeniedException();
            }
        }
        if (!authorization.organizationAdmin()
                && !authorization.assignedBranchIds().containsAll(selection.branchIds())) {
            throw new ReportingAccessDeniedException();
        }

        List<GymBranchSummary> branches = List.of();
        ZoneId timezone;
        if (selection.scope() == ReportingScope.ORGANIZATION) {
            timezone = organizationTimezone();
        } else {
            branches = branchMetadata(selection.branchIds(), includeInactive);
            if (branches.size() != selection.branchIds().size()) {
                throw new ReportingAccessDeniedException();
            }
            timezone = branches.size() == 1
                    ? ZoneId.of(branches.getFirst().timezone())
                    : organizationTimezone();
        }

        ReportingRange range;
        ReportingQueryWindow queryWindow;
        try {
            range = ReportingRange.of(fromInclusive, toExclusive, timezone.getId());
            rangePolicy.validate(range, granularity);
            LocalDate operationalDate = LocalDate.now(clock.withZone(timezone));
            if (toExclusive.isAfter(operationalDate.plusDays(1))) {
                throw new ReportingValidationException(
                        "Reporting dates must not extend into the future.");
            }
            queryWindow = new ReportingQueryWindow(fromInclusive, toExclusive, timezone);
        } catch (ReportingValidationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ReportingValidationException("Reporting date range is invalid.");
        }

        LocalDate operationalDate = LocalDate.now(clock.withZone(timezone));
        ReportingContext context = new ReportingContext(selection, range, clock.instant());
        ReportingQueryScope queryScope = selection.scope() == ReportingScope.ORGANIZATION
                ? ReportingQueryScope.organization()
                : ReportingQueryScope.branches(selection.branchIds());
        return new ResolvedReport(
                context, queryScope, queryWindow, operationalDate, List.copyOf(branches));
    }

    private BranchReportingSelection defaultSelection(StaffAuthorizationContext authorization) {
        if (authorization.organizationAdmin()) {
            return BranchReportingSelection.organizationWide();
        }
        StaffBranchContext context = activeContext(authorization.userId());
        if (context.activeBranchId() == null) {
            throw new ReportingAccessDeniedException();
        }
        return BranchReportingSelection.activeBranch(context.activeBranchId());
    }

    private StaffBranchContext activeContext(UUID userId) {
        try {
            return Objects.requireNonNull(activeBranchContextResolver.resolve(userId));
        } catch (ActiveBranchContextUnavailableException exception) {
            throw new ReportingAccessDeniedException();
        } catch (RuntimeException exception) {
            throw new ReportingDataAccessException(exception);
        }
    }

    private StaffAuthorizationContext authorization(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null) {
            throw new ReportingAccessDeniedException();
        }
        StaffAuthorizationContext context;
        try {
            context = staffScopeQuery.findAuthorizationContext(actor.id()).orElse(null);
        } catch (RuntimeException exception) {
            throw new ReportingDataAccessException(exception);
        }
        if (context == null || !actor.id().equals(context.userId())
                || context.accountStatus() != StaffAccountStatus.ACTIVE) {
            throw new ReportingAccessDeniedException();
        }
        return context;
    }

    private ZoneId organizationTimezone() {
        OrganizationDetails organization;
        try {
            organization = organizationQuery.findCanonical().orElse(null);
        } catch (RuntimeException exception) {
            throw new ReportingDataAccessException(exception);
        }
        if (organization == null) {
            throw new ReportingDataAccessException(
                    new IllegalStateException("Canonical reporting organization is unavailable."));
        }
        if (organization.status() != OrganizationStatus.ACTIVE) {
            throw new ReportingAccessDeniedException();
        }
        return ZoneId.of(organization.defaultTimezone());
    }

    private List<GymBranchSummary> branchMetadata(
            List<UUID> branchIds,
            boolean includeInactive) {
        try {
            List<GymBranchSummary> branches = branchQuery.findCanonicalBranches(
                    branchIds, includeInactive);
            if (branches == null || branches.stream().anyMatch(Objects::isNull)) {
                throw new ReportingDataAccessException(
                        new IllegalStateException("Reporting branch metadata was invalid."));
            }
            Set<UUID> requested = Set.copyOf(branchIds);
            Set<UUID> returned = new HashSet<>();
            for (GymBranchSummary branch : branches) {
                if (!requested.contains(branch.id()) || !returned.add(branch.id())
                        || (!includeInactive && branch.status() != GymBranchStatus.ACTIVE)) {
                    throw new ReportingDataAccessException(
                            new IllegalStateException("Reporting branch metadata was inconsistent."));
                }
            }
            return branches.stream()
                    .sorted(java.util.Comparator.comparing(GymBranchSummary::code)
                            .thenComparing(GymBranchSummary::id))
                    .toList();
        } catch (GymBranchReportingUnavailableException exception) {
            throw new ReportingDataAccessException(exception);
        }
    }

    private static void requireAllowed(
            StaffAuthorizationContext actor,
            ReportingMetricGroup metric,
            BranchReportingSelection selection) {
        if (!ReportingAccessPolicy.allows(actor, metric, selection)) {
            throw new ReportingAccessDeniedException();
        }
    }

    private static <T> T read(Supplier<T> query) {
        try {
            return Objects.requireNonNull(query.get(), "A reporting source returned no projection.");
        } catch (ReportingDataAccessException exception) {
            throw exception;
        } catch (PaymentReportingUnavailableException
                 | ClientReportingUnavailableException
                 | MembershipReportingUnavailableException
                 | AccessReportingUnavailableException
                 | EquipmentReportingUnavailableException
                 | IncidentReportingUnavailableException
                 | MaintenanceReportingUnavailableException
                 | EmailDeliveryReportingUnavailableException exception) {
            throw new ReportingDataAccessException(exception);
        }
    }

    private static <S, T> Map<UUID, T> indexByBranch(
            List<S> values,
            Function<S, UUID> branchId,
            Function<S, T> projection,
            Set<UUID> expectedIds) {
        Objects.requireNonNull(values, "Branch reporting rows are required.");
        Map<UUID, T> indexed = new HashMap<>();
        for (S value : values) {
            if (value == null) {
                throw new ReportingDataAccessException(
                        new IllegalStateException("A branch reporting row was null."));
            }
            UUID id = branchId.apply(value);
            T summary = projection.apply(value);
            if (id == null || summary == null || !expectedIds.contains(id)
                    || indexed.putIfAbsent(id, summary) != null) {
                throw new ReportingDataAccessException(
                        new IllegalStateException("Branch reporting rows were inconsistent."));
            }
        }
        return Map.copyOf(indexed);
    }

    private record ResolvedReport(
            ReportingContext context,
            ReportingQueryScope queryScope,
            ReportingQueryWindow queryWindow,
            LocalDate operationalDate,
            List<GymBranchSummary> branches) {
    }
}
