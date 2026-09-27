package io.github.guillermodubon.coachgym.plan.application;

import io.github.guillermodubon.coachgym.plan.PlanDetails;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageDetails;
import io.github.guillermodubon.coachgym.plan.MembershipPlanSaleCoverage;
import io.github.guillermodubon.coachgym.plan.UpdateMembershipPlanBranchCoverageCommand;
import io.github.guillermodubon.coachgym.plan.domain.PlanDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PlanStore {

    PlanDetails create(PlanDefinition definition, AuthenticatedActor actor, Instant occurredAt);

    PlanPage findAll(PlanSearchQuery query);

    PlanPage findAllForBranch(PlanSearchQuery query, UUID branchId);

    Optional<PlanDetails> findById(UUID id);

    Optional<MembershipPlanBranchCoverageDetails> findBranchCoverage(UUID planId);

    Optional<MembershipPlanSaleCoverage> findSaleCoverage(
            UUID planId,
            UUID registrationBranchId);

    boolean isValidAtBranch(UUID planId, UUID branchId);

    MembershipPlanBranchCoverageDetails replaceBranchCoverage(
            UUID planId,
            UpdateMembershipPlanBranchCoverageCommand command,
            AuthenticatedActor actor,
            Instant occurredAt);

    PlanDetails update(
            UUID id,
            PlanDefinition definition,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt);

    PlanDetails changeActive(
            UUID id,
            boolean active,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt);

    List<PlanDetails> findByIds(
            Set<UUID> planIds);
}
