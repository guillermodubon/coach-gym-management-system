package io.github.guillermodubon.coachgym.plan;

import java.util.Optional;
import java.util.UUID;

/** Public read boundary for authoritative plan branch coverage. */
@FunctionalInterface
public interface MembershipPlanBranchCoverageQuery {

    /** Returns the current coverage definition for a plan, when it exists. */
    Optional<MembershipPlanBranchCoverageDetails> findCoverage(UUID planId);
}
