package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodCoverageSummary;
import java.util.Objects;

/** Internal read projection with the persisted entitlement summary for its current period. */
public record MembershipReadEntry(
        MembershipDetails membership,
        MembershipPeriodCoverageSummary currentPeriodCoverage) {

    public MembershipReadEntry {
        membership = Objects.requireNonNull(membership, "Membership details are required.");
        if (membership.currentPeriod() == null) {
            throw new IllegalArgumentException("Current membership period is required.");
        }
        currentPeriodCoverage = Objects.requireNonNull(
                currentPeriodCoverage, "Current period coverage is required.");
    }
}
