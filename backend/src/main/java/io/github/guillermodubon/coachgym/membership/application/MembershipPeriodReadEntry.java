package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.membership.MembershipPeriodCoverageSummary;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodDetails;
import java.util.Objects;

/** Period snapshot and its privacy-minimized purchased coverage. */
public record MembershipPeriodReadEntry(
        MembershipPeriodDetails period,
        MembershipPeriodCoverageSummary coverage) {

    public MembershipPeriodReadEntry {
        period = Objects.requireNonNull(period, "Membership period is required.");
        coverage = Objects.requireNonNull(coverage, "Period coverage is required.");
    }
}
