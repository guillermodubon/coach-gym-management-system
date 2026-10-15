package io.github.guillermodubon.coachgym.configuration;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable persisted projection of the access-payment policy.
 *
 * <p>The audit actor and timestamp are server-owned values. They are exposed
 * for read-side projections only and are not accepted by update commands.</p>
 */
public record AccessPaymentPolicyDetails(
        boolean requireConfirmedPaymentForAccess,
        long version,
        Instant updatedAt,
        UUID updatedByUserId) {

    public AccessPaymentPolicyDetails {
        if (version < 0) {
            throw new AccessPaymentPolicyValidationException(
                    "Access payment policy version must not be negative.");
        }
    }

    /** Convenience projection for an unpersisted policy value. */
    public AccessPaymentPolicyDetails(
            boolean requireConfirmedPaymentForAccess,
            long version) {
        this(requireConfirmedPaymentForAccess, version, null, null);
    }

    /** Returns the technology-neutral policy value represented by this row. */
    public AccessPaymentPolicy policy() {
        return new AccessPaymentPolicy(requireConfirmedPaymentForAccess);
    }
}
