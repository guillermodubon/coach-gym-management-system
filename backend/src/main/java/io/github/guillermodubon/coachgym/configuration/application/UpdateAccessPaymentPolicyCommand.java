package io.github.guillermodubon.coachgym.configuration.application;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyValidationException;

/**
 * Administrative request to change the access-payment requirement.
 *
 * <p>Actor identity and update time are intentionally absent: both are
 * captured by the authenticated server-side workflow. The expected version
 * prevents lost updates.</p>
 */
public record UpdateAccessPaymentPolicyCommand(
        boolean requireConfirmedPaymentForAccess,
        long expectedVersion) {

    public UpdateAccessPaymentPolicyCommand {
        if (expectedVersion < 0) {
            throw new AccessPaymentPolicyValidationException(
                    "Expected access payment policy version must not be negative.");
        }
    }
}
