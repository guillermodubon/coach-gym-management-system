package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.MembershipPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodCoverageSummary;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MembershipPeriodResponse(
        UUID id,
        short periodNumber,
        MembershipPeriodSource source,
        MembershipPricingResponse pricing,
        LocalDate startsOn,
        LocalDate baseEndsOn,
        LocalDate effectiveEndsOn,
        Instant createdAt,
        long version,
        UUID registeredAtBranchId,
        MembershipPeriodCoverageResponse coverage) {

    static MembershipPeriodResponse from(
            MembershipPeriodDetails period) {
        return from(period, null);
    }

    static MembershipPeriodResponse from(
            MembershipPeriodDetails period,
            MembershipPeriodCoverageSummary coverage) {

        return new MembershipPeriodResponse(
                period.id(),
                period.periodNumber(),
                period.source(),
                MembershipPricingResponse.from(
                        period.pricing()),
                period.startsOn(),
                period.baseEndsOn(),
                period.effectiveEndsOn(),
                period.createdAt(),
                period.version(),
                period.registeredAtBranchId(),
                MembershipPeriodCoverageResponse.from(coverage));
    }
}
