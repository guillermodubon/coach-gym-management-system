package io.github.guillermodubon.coachgym.membership;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Event emitted after an initial membership and its first period have
 * been persisted successfully.
 */
public record MembershipCreated(
        UUID membershipId,
        String membershipCode,
        UUID clientId,
        UUID membershipPeriodId,
        UUID membershipPlanId,
        UUID promotionId,
        BigDecimal listPrice,
        BigDecimal discountAmount,
        BigDecimal finalPrice,
        String currency,
        LocalDate startsOn,
        LocalDate effectiveEndsOn,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {
}
