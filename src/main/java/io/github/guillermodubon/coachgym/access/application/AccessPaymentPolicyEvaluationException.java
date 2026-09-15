package io.github.guillermodubon.coachgym.access.application;

/**
 * Safe application failure raised when the payment requirement cannot be
 * evaluated reliably for an access attempt.
 *
 * <p>The access workflow must fail closed with a technical error in this
 * situation. It must never turn an unavailable policy or payment query into
 * a {@code PAYMENT_REQUIRED} business denial.</p>
 */
public final class AccessPaymentPolicyEvaluationException
        extends RuntimeException {

    public AccessPaymentPolicyEvaluationException(String message) {
        super(message);
    }

    public AccessPaymentPolicyEvaluationException(
            String message,
            Throwable cause) {
        super(message, cause);
    }
}
