package io.github.guillermodubon.coachgym.membership;

import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import java.time.Instant;
import java.util.UUID;

/** Privacy-safe event emitted after a new period's exact branch coverage is captured. */
public record MembershipPeriodCoverageCaptured(
        UUID membershipId,
        UUID membershipPeriodId,
        UUID membershipPlanId,
        UUID registeredAtBranchId,
        MembershipPlanBranchCoverageScope coverageScope,
        int coveredBranchCount,
        long sourcePlanVersion,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public MembershipPeriodCoverageCaptured {
        if (membershipId == null || membershipPeriodId == null
                || membershipPlanId == null || registeredAtBranchId == null
                || coverageScope == null || actorUserId == null
                || actorIdentifier == null || actorIdentifier.isBlank()
                || occurredAt == null || coveredBranchCount < 1
                || sourcePlanVersion < 0) {
            throw new IllegalArgumentException(
                    "Membership period coverage event fields are invalid.");
        }
    }
}
