package io.github.guillermodubon.coachgym.membership;

import java.util.Optional;
import java.util.UUID;

/**
 * Public membership-module boundary used by the payment module.
 *
 * <p>Exposes two separate lookups so the application layer can
 * produce distinct error codes for a missing membership
 * ({@code PAYMENT_MEMBERSHIP_NOT_FOUND}), a missing period
 * ({@code PAYMENT_PERIOD_NOT_FOUND}), and a period that does not
 * belong to the given membership ({@code PAYMENT_PERIOD_MISMATCH}).</p>
 */
public interface MembershipPaymentQuery {

    /**
     * Returns the minimal membership projection needed for payment
     * validation, or empty if the membership does not exist.
     */
    Optional<MembershipPaymentDetails> findMembershipForPayment(
            UUID membershipId);

    /**
     * Returns the minimal period projection needed for payment
     * validation, or empty if no period with that ID exists.
     *
     * <p>The returned record contains {@code membershipId}, so the
     * application layer can distinguish a missing period
     * ({@code PAYMENT_PERIOD_NOT_FOUND}) from a period that exists
     * under a different membership ({@code PAYMENT_PERIOD_MISMATCH}).</p>
     */
    Optional<MembershipPaymentPeriodDetails> findPeriodForPayment(
            UUID periodId);
}
