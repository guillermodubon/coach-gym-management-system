package io.github.guillermodubon.coachgym.membership;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Minimal membership-period projection used by the payment module.
 *
 * <p>Provides the period's membership association and the pricing
 * snapshot fields required for payment validation (finalPrice,
 * currency) without exposing internal domain types.</p>
 */
public record MembershipPaymentPeriodDetails(
        UUID periodId,
        UUID membershipId,
        BigDecimal finalPrice,
        String currency) {
}
