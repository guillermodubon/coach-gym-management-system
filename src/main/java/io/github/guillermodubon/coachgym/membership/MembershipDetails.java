package io.github.guillermodubon.coachgym.membership;

import java.time.Instant;
import java.util.UUID;

public record MembershipDetails(
        UUID id,
        String membershipCode,
        UUID clientId,
        MembershipStatus status,
        MembershipPeriodDetails currentPeriod,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
