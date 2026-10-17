package io.github.guillermodubon.coachgym.access.domain;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicy;

/**
 * Pure access decision step for the optional confirmed-payment requirement.
 *
 * <p>The application service remains responsible for deciding when to invoke
 * the payment query. This evaluator receives only its boolean result, so it
 * cannot access payment persistence or provider state and cannot replace an
 * earlier access denial.</p>
 */
public final class AccessPaymentPolicyEvaluator {

    private static final String PAYMENT_REQUIRED_REASON =
            "A confirmed payment is required for access.";

    private AccessPaymentPolicyEvaluator() {
    }

    /**
     * Applies the payment requirement to an already evaluated access result.
     *
     * <p>Earlier denials always win. A disabled policy and a qualifying payment
     * both preserve the supplied evaluation.</p>
     *
     * @param baseEvaluation result of the existing non-financial access policy
     * @param policy immutable global policy projection
     * @param hasConfirmedPayment result of the minimal payment query
     * @return the unchanged base result or a safe {@code PAYMENT_REQUIRED}
     *         denial
     */
    public static AccessEvaluation evaluate(
            AccessEvaluation baseEvaluation,
            AccessPaymentPolicy policy,
            boolean hasConfirmedPayment) {

        if (baseEvaluation == null) {
            throw new IllegalArgumentException(
                    "Base access evaluation must be provided.");
        }
        if (policy == null) {
            throw new IllegalArgumentException(
                    "Access payment policy must be provided.");
        }

        if (baseEvaluation.result() != AccessResult.ALLOWED
                || !policy.requireConfirmedPaymentForAccess()
                || hasConfirmedPayment) {
            return baseEvaluation;
        }

        return AccessEvaluation.denied(
                AccessReasonCode.PAYMENT_REQUIRED,
                PAYMENT_REQUIRED_REASON);
    }
}
