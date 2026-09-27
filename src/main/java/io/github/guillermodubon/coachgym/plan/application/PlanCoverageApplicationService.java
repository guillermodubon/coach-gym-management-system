package io.github.guillermodubon.coachgym.plan.application;

import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageDetails;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageQuery;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchEligibilityQuery;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import io.github.guillermodubon.coachgym.plan.MembershipPlanSaleCoverage;
import io.github.guillermodubon.coachgym.plan.MembershipPlanSaleCoverageQuery;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageValidationException;
import io.github.guillermodubon.coachgym.plan.MembershipPlanCoverageChanged;
import io.github.guillermodubon.coachgym.plan.PlanDetails;
import io.github.guillermodubon.coachgym.plan.UpdateMembershipPlanBranchCoverageCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.BranchOperationContext;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationPolicy;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffBranchAuthorizationException;
import io.github.guillermodubon.coachgym.user.StaffScopeAuthorizationPolicy;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary for membership-plan branch coverage and visibility. */
@Service
public class PlanCoverageApplicationService implements
        MembershipPlanBranchCoverageQuery,
        MembershipPlanBranchEligibilityQuery,
        MembershipPlanSaleCoverageQuery {

    private final PlanStore planStore;
    private final StaffScopeQuery staffScopeQuery;
    private final BranchOperationContextResolver branchContextResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PlanCoverageApplicationService(
            PlanStore planStore,
            StaffScopeQuery staffScopeQuery,
            BranchOperationContextResolver branchContextResolver,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.planStore = Objects.requireNonNull(planStore);
        this.staffScopeQuery = Objects.requireNonNull(staffScopeQuery);
        this.branchContextResolver = Objects.requireNonNull(branchContextResolver);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MembershipPlanBranchCoverageDetails> findCoverage(UUID planId) {
        Objects.requireNonNull(planId, "Plan ID is required.");
        return planStore.findBranchCoverage(planId);
    }

    /** Reads the complete cross-branch definition only for an active organization administrator. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public MembershipPlanBranchCoverageDetails findCoverageForAdministration(
            UUID planId,
            AuthenticatedActor actor) {
        Objects.requireNonNull(planId, "Plan ID is required.");
        requireOrganizationAdministrator(actor);
        return planStore.findBranchCoverage(planId)
                .orElseThrow(() -> new PlanNotFoundException(planId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isValidAtBranch(UUID planId, UUID branchId) {
        Objects.requireNonNull(planId, "Plan ID is required.");
        Objects.requireNonNull(branchId, "Branch ID is required.");
        return planStore.isValidAtBranch(planId, branchId);
    }

    @Override
    @Transactional
    public Optional<MembershipPlanSaleCoverage> findForSale(
            UUID planId,
            UUID registrationBranchId) {
        Objects.requireNonNull(planId, "Plan ID is required.");
        Objects.requireNonNull(registrationBranchId, "Registration branch ID is required.");
        return planStore.findSaleCoverage(planId, registrationBranchId);
    }

    /**
     * Atomically replaces a plan's complete coverage definition. Only an
     * active organization-scoped administrator may make this change.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MembershipPlanBranchCoverageDetails replaceCoverage(
            UUID planId,
            UpdateMembershipPlanBranchCoverageCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(planId, "Plan ID is required.");
        Objects.requireNonNull(command, "Coverage command is required.");
        requireActor(actor);

        StaffAuthorizationContext staff = staffScopeQuery.findAuthorizationContext(actor.id())
                .orElseThrow(() -> new StaffBranchAuthorizationException(
                        "An active organization administrator is required to change plan coverage."));
        if (!staff.userId().equals(actor.id())) {
            throw new StaffBranchAuthorizationException(
                    "An active organization administrator is required to change plan coverage.");
        }
        StaffScopeAuthorizationPolicy.requireOrganizationAdministrator(staff);

        BranchOperationContext branchContext = branchContextResolver.resolveOperation(actor.id());
        if (!branchContext.userId().equals(actor.id()) || !branchContext.organizationWide()) {
            throw new StaffBranchAuthorizationException(
                    "An active organization administrator is required to change plan coverage.");
        }
        if (!branchContext.authorizedActiveBranchIds().containsAll(command.branchIds())
                || (branchContext.authorizedActiveBranchIds().isEmpty()
                    && command.scope() == MembershipPlanBranchCoverageScope.ALL_BRANCHES)) {
            throw new MembershipPlanBranchCoverageValidationException(
                    "Coverage may reference only active branches in the canonical organization.");
        }

        Instant occurredAt = clock.instant();
        MembershipPlanBranchCoverageDetails updated = planStore.replaceBranchCoverage(
                planId,
                command,
                actor,
                occurredAt);
        eventPublisher.publishEvent(new MembershipPlanCoverageChanged(
                updated.planId(),
                updated.scope(),
                updated.branchIds().size(),
                updated.version(),
                actor.id(),
                actor.username(),
                occurredAt));
        return updated;
    }

    /**
     * Returns plans valid for one server-resolved active branch. An
     * organization administrator may select another authorized active branch;
     * a request branch ID never grants authority or broadens the result set.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PlanPage findVisiblePlans(
            PlanSearchQuery query,
            UUID requestedBranchId,
            AuthenticatedActor actor) {
        Objects.requireNonNull(query, "Plan query is required.");
        requireActor(actor);
        BranchOperationContext context = branchContextResolver.resolveOperation(actor.id());
        if (!context.userId().equals(actor.id())) {
            throw new StaffBranchAuthorizationException(
                    "The authenticated staff branch context is not available.");
        }
        UUID branchId = BranchResourceAuthorizationPolicy.requireListBranch(
                context, requestedBranchId);
        return planStore.findAllForBranch(query, branchId);
    }

    /** Hides plans that are not valid at the actor's server-resolved active branch. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PlanDetails findVisiblePlan(UUID planId, AuthenticatedActor actor) {
        Objects.requireNonNull(planId, "Plan ID is required.");
        requireActor(actor);
        BranchOperationContext context = branchContextResolver.resolveOperation(actor.id());
        if (!context.userId().equals(actor.id())) {
            throw new StaffBranchAuthorizationException(
                    "The authenticated staff branch context is not available.");
        }
        UUID branchId = BranchResourceAuthorizationPolicy.requireActiveBranch(context);
        PlanDetails plan = planStore.findById(planId)
                .orElseThrow(() -> new PlanNotFoundException(planId));
        if (!planStore.isValidAtBranch(planId, branchId)) {
            throw new PlanNotFoundException(planId);
        }
        return plan;
    }

    private static void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null
                || actor.username() == null || actor.username().isBlank()) {
            throw new StaffBranchAuthorizationException(
                    "The authenticated staff context is not available.");
        }
    }

    private void requireOrganizationAdministrator(AuthenticatedActor actor) {
        requireActor(actor);
        StaffAuthorizationContext staff = staffScopeQuery.findAuthorizationContext(actor.id())
                .filter(context -> context.userId().equals(actor.id()))
                .orElseThrow(() -> new StaffBranchAuthorizationException(
                        "An active organization administrator is required for this plan operation."));
        StaffScopeAuthorizationPolicy.requireOrganizationAdministrator(staff);
    }
}
