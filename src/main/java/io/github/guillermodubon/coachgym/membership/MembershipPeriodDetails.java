package io.github.guillermodubon.coachgym.membership;

import io.github.guillermodubon.coachgym.membership.domain.MembershipPricingSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MembershipPeriodDetails(
        UUID id,
        short periodNumber,
        MembershipPeriodSource source,
        MembershipPricingSnapshot pricing,
        LocalDate startsOn,
        LocalDate baseEndsOn,
        LocalDate effectiveEndsOn,
        Instant createdAt,
        long version) {
}
