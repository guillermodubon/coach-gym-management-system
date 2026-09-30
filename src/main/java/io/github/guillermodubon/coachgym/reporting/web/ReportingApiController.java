package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.reporting.AdministratorReportingDashboardSummary;
import io.github.guillermodubon.coachgym.reporting.BranchComparisonRequest;
import io.github.guillermodubon.coachgym.reporting.BranchReportingSelection;
import io.github.guillermodubon.coachgym.reporting.ReceptionistReportingDashboardSummary;
import io.github.guillermodubon.coachgym.reporting.ReportingDashboardRequest;
import io.github.guillermodubon.coachgym.reporting.ReportingDashboardSummary;
import io.github.guillermodubon.coachgym.reporting.ReportingGranularity;
import io.github.guillermodubon.coachgym.reporting.ReportingScope;
import io.github.guillermodubon.coachgym.reporting.ReportingTrendRequest;
import io.github.guillermodubon.coachgym.reporting.application.ReportingAccessDeniedException;
import io.github.guillermodubon.coachgym.reporting.application.ReportingCompositionApplicationService;
import io.github.guillermodubon.coachgym.reporting.application.ReportingValidationException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reporting")
@Tag(name = "Multi-branch Reporting", description = "Scope-authorized operational reports.")
class ReportingApiController {

    private final ReportingCompositionApplicationService service;

    ReportingApiController(ReportingCompositionApplicationService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Get a scope-authorized reporting summary",
            description = "Uses the persisted staff scope and assignments. Omitted scope resolves "
                    + "to organization scope for organization administrators and the active branch "
                    + "for branch staff. Receptionists receive membership and access summaries only. "
                    + "The date interval is half-open [fromInclusive, toExclusive) in the returned "
                    + "business timezone. Request branch IDs are filters, never authority.",
            security = @SecurityRequirement(name = "sessionCookie"))
    ReportingDashboardResponse summary(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) List<UUID> branchIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromInclusive,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toExclusive,
            Authentication authentication) {
        validateRange(fromInclusive, toExclusive);
        BranchReportingSelection selection = selection(scope, branchIds, true);
        ReportingDashboardSummary result = service.dashboard(
                new ReportingDashboardRequest(selection, fromInclusive, toExclusive),
                actor(authentication));
        if (result instanceof AdministratorReportingDashboardSummary administrator) {
            return new ReportingAdministratorDashboardResponse(
                    "ADMINISTRATOR",
                    ReportingApiContextResponse.from(administrator.context()),
                    administrator.metrics());
        }
        if (result instanceof ReceptionistReportingDashboardSummary receptionist) {
            return new ReportingReceptionistDashboardResponse(
                    "RECEPTIONIST",
                    ReportingApiContextResponse.from(receptionist.context()),
                    receptionist.memberships(),
                    receptionist.access());
        }
        throw new ReportingAccessDeniedException();
    }

    @GetMapping("/financial-trend")
    @Operation(
            summary = "Get a bounded paid-payment trend",
            description = "ADMIN only. The persisted organization scope may query organization or "
                    + "authorized branch selection; a branch administrator may query actively "
                    + "assigned branches only, and RECEPTIONIST is denied. "
                    + "Requires an explicit organization or authorized branch selection. "
                    + "The interval is half-open [fromInclusive, toExclusive) and currency values "
                    + "remain separate. Branch IDs are filters, never authority.",
            security = @SecurityRequirement(name = "sessionCookie"))
    FinancialTrendResponse financialTrend(
            @RequestParam String scope,
            @RequestParam(required = false) List<UUID> branchIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromInclusive,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toExclusive,
            @RequestParam(defaultValue = "DAILY") ReportingGranularity granularity,
            Authentication authentication) {
        validateRange(fromInclusive, toExclusive);
        return FinancialTrendResponse.from(service.paidTrend(
                new ReportingTrendRequest(
                        selection(scope, branchIds, false),
                        fromInclusive,
                        toExclusive,
                        granularity),
                actor(authentication)));
    }

    @GetMapping("/access-trend")
    @Operation(
            summary = "Get a bounded daily access trend",
            description = "ADMIN only. The persisted organization scope may query organization or "
                    + "authorized branch selection; a branch administrator may query actively "
                    + "assigned branches only, and RECEPTIONIST is denied. "
                    + "Requires an explicit organization or authorized branch selection. "
                    + "The interval is half-open [fromInclusive, toExclusive) in the returned "
                    + "business timezone. Only daily buckets are supported. No credential or "
                    + "personal data is returned.",
            security = @SecurityRequirement(name = "sessionCookie"))
    AccessTrendResponse accessTrend(
            @RequestParam String scope,
            @RequestParam(required = false) List<UUID> branchIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromInclusive,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toExclusive,
            @Parameter(description = "Only DAILY is supported")
            @RequestParam(defaultValue = "DAILY") ReportingGranularity granularity,
            Authentication authentication) {
        validateRange(fromInclusive, toExclusive);
        return AccessTrendResponse.from(service.accessTrend(
                new ReportingTrendRequest(
                        selection(scope, branchIds, false),
                        fromInclusive,
                        toExclusive,
                        granularity),
                actor(authentication)));
    }

    @GetMapping("/branches/comparison")
    @Operation(
            summary = "Compare authorized branches",
            description = "ADMIN only. Organization administrators may compare authorized "
                    + "branches; branch administrators may compare only actively assigned branches; "
                    + "RECEPTIONIST is denied. Requires between 2 and 20 explicitly selected "
                    + "branches. Only an organization administrator may include inactive branch history. "
                    + "Every branch ID is re-authorized against persisted staff scope.",
            security = @SecurityRequirement(name = "sessionCookie"))
    BranchComparisonResponse compareBranches(
            @RequestParam List<UUID> branchIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromInclusive,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toExclusive,
            @RequestParam(defaultValue = "false") boolean includeInactiveBranches,
            Authentication authentication) {
        validateRange(fromInclusive, toExclusive);
        if (branchIds == null || branchIds.size() < 2 || branchIds.size() > 20) {
            throw new ReportingValidationException(
                    "Branch comparison requires between 2 and 20 branch identifiers.");
        }
        BranchReportingSelection selection = selection(
                ReportingScope.AUTHORIZED_BRANCH_SET.name(), branchIds, false);
        return BranchComparisonResponse.from(service.compareBranches(
                new BranchComparisonRequest(
                        selection, fromInclusive, toExclusive, includeInactiveBranches),
                actor(authentication)));
    }

    private static BranchReportingSelection selection(
            String requestedScope,
            List<UUID> requestedBranchIds,
            boolean allowDefault) {
        List<UUID> branchIds = requestedBranchIds == null ? List.of() : List.copyOf(requestedBranchIds);
        if (branchIds.size() > 20) {
            throw new ReportingValidationException(
                    "Reporting selection exceeds the maximum branch count.");
        }
        if (requestedScope == null || requestedScope.isBlank()) {
            if (allowDefault && branchIds.isEmpty()) {
                return null;
            }
            throw new ReportingValidationException("A reporting scope is required.");
        }
        final ReportingScope scope;
        try {
            scope = ReportingScope.valueOf(requestedScope.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ReportingValidationException("The reporting scope is invalid.");
        }
        try {
            return switch (scope) {
                case ORGANIZATION -> {
                    requireCount(branchIds, 0, 0);
                    yield BranchReportingSelection.organizationWide();
                }
                case SINGLE_BRANCH -> {
                    requireCount(branchIds, 1, 1);
                    yield BranchReportingSelection.singleBranch(branchIds.getFirst());
                }
                case ACTIVE_BRANCH -> {
                    requireCount(branchIds, 1, 1);
                    yield BranchReportingSelection.activeBranch(branchIds.getFirst());
                }
                case AUTHORIZED_BRANCH_SET -> {
                    requireCount(branchIds, 2, 20);
                    yield BranchReportingSelection.authorizedBranches(branchIds);
                }
            };
        } catch (IllegalArgumentException exception) {
            throw new ReportingValidationException("The reporting scope filters are invalid.");
        }
    }

    private static void requireCount(List<UUID> branchIds, int minimum, int maximum) {
        if (branchIds.size() < minimum || branchIds.size() > maximum) {
            throw new IllegalArgumentException("Invalid branch filter count.");
        }
    }

    private static void validateRange(LocalDate fromInclusive, LocalDate toExclusive) {
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new ReportingValidationException(
                    "Reporting dates must define a non-empty half-open interval.");
        }
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CoachGymUserPrincipal principal)) {
            throw new ReportingAccessDeniedException();
        }
        return principal.authenticatedActor();
    }
}
