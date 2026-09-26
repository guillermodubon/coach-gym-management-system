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
        Instant occurredAt,
        UUID branchId
) {
    public MembershipRenewed(
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
            Instant occurredAt) {
        this(membershipId, membershipCode, clientId, membershipPeriodId,
                periodNumber, membershipPlanId, promotionId, listPrice,
                discountAmount, finalPrice, currency, startsOn,
                effectiveEndsOn, previousStatus, resultingStatus, actorUserId,
                actorIdentifier, occurredAt, null);
    }

    public UUID registeredAtBranchId() { return branchId; }
}
