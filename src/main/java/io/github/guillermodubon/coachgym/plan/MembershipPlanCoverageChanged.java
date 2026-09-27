package io.github.guillermodubon.coachgym.plan;

import java.time.Instant;
import java.util.UUID;

/** Privacy-minimized event published after a plan's branch coverage changes. */
public record MembershipPlanCoverageChanged(
        UUID planId,
        MembershipPlanBranchCoverageScope scope,
        int explicitBranchCount,
        long planVersion,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public MembershipPlanCoverageChanged {
        if (planId == null || scope == null || actorUserId == null
                || actorIdentifier == null || actorIdentifier.isBlank()
                || occurredAt == null) {
            throw new IllegalArgumentException("Plan coverage event fields are required.");
        }
        if (explicitBranchCount < 0 || planVersion < 0) {
            throw new IllegalArgumentException("Plan coverage event counts and version must not be negative.");
        }
        actorIdentifier = actorIdentifier.strip();
    }
}
