package io.github.guillermodubon.coachgym.configuration;

/**
 * Immutable projection of the global access-payment requirement.
 *
 * <p>The value is deliberately technology-neutral. Persistence, actor
 * attribution, optimistic locking, and administrative authorization belong to
 * the settings application workflow, not this value object.</p>
 */
public record AccessPaymentPolicy(
        boolean requireConfirmedPaymentForAccess) {

    /** Backward-compatible default used when the setting is first introduced. */
    public static final boolean DEFAULT_REQUIRE_CONFIRMED_PAYMENT_FOR_ACCESS = false;

    /** Returns the backward-compatible disabled policy. */
    public static AccessPaymentPolicy disabled() {
        return new AccessPaymentPolicy(false);
    }

    /** Returns the policy that requires a confirmed payment. */
    public static AccessPaymentPolicy enabled() {
        return new AccessPaymentPolicy(true);
    }
}
