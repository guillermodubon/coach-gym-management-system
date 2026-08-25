package io.github.guillermodubon.coachgym.membership;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MembershipCancellationDetails(
        UUID membershipId,
        UUID membershipPeriodId,
        MembershipStatus previousStatus,
        LocalDate cancelledOn,
        String reason,
        UUID cancelledByUserId,
        Instant occurredAt,
        long membershipVersion) {
}
