package io.github.guillermodubon.coachgym.payment;

import java.util.UUID;

/**
 * Minimal payment-module boundary used by access evaluation.
 *
 * <p>The implementation answers whether a persisted, currently {@link
 * PaymentStatus#PAID PAID} payment exists for the exact client, membership,
 * and membership-period tuple. It must not call an external provider or
 * expose payment details, checkout data, or persistence types.</p>
 */
@FunctionalInterface
public interface ConfirmedPaymentForAccessQuery {

    /**
     * Returns whether the exact membership period has a qualifying payment.
     *
     * @param clientId authoritative client selected by access resolution
     * @param membershipId authoritative membership selected by access resolution
     * @param membershipPeriodId authoritative current period selected by access
     * @return {@code true} only when a persisted {@code PAID} payment qualifies
     */
    boolean hasConfirmedPaymentForPeriod(
            UUID clientId,
            UUID membershipId,
            UUID membershipPeriodId);
}
