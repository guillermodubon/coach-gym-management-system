package io.github.guillermodubon.coachgym.membership;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Event emitted after a membership renewal has been persisted
 * successfully.
 */
public record MembershipRenewed(
        UUID membershipId,
        String membershipCode,
        UUID clientId,
        UUID membershipPeriodId,
        short periodNumber,
        UUID membershipPlanId,
        UUID promotionId,
        BigDecimal listPrice,
        BigDecimal discountAmount,
        BigDecimal finalPrice,
        String currency,
        LocalDate startsOn,
        LocalDate effectiveEndsOn,
        MembershipStatus previousStatus,
        MembershipStatus resultingStatus,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt
) { }
