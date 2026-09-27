package io.github.guillermodubon.coachgym.membership;

import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable branch entitlement captured for one membership period. The
 * explicit branch IDs are authoritative even when the source plan scope was
 * {@code ALL_BRANCHES}; later branch openings do not expand this snapshot.
 */
public record MembershipPeriodBranchCoverageDetails(
        UUID membershipPeriodId,
        MembershipPlanBranchCoverageScope scopeSnapshot,
        Set<UUID> coveredBranchIds,
        Instant capturedAt,
        long sourcePlanVersion) {

    public MembershipPeriodBranchCoverageDetails {
        if (membershipPeriodId == null) {
            throw new MembershipPeriodBranchCoverageValidationException(
                    "Membership period ID is required.");
        }
        if (capturedAt == null) {
            throw new MembershipPeriodBranchCoverageValidationException(
                    "Coverage snapshot time is required.");
        }
        if (sourcePlanVersion < 0) {
            throw new MembershipPeriodBranchCoverageValidationException(
                    "Source plan version must not be negative.");
        }
        coveredBranchIds = MembershipPeriodBranchCoveragePolicy
                .normalizeSnapshot(scopeSnapshot, coveredBranchIds);
    }
}
