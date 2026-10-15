package io.github.guillermodubon.coachgym.membership;

import java.util.UUID;

/**
 * Minimal membership projection used by the payment module.
 *
 * <p>Exposes only what payment registration needs to validate
 * ownership and state. Does not expose JPA entities or
 * internal membership domain types.</p>
 */
public record MembershipPaymentDetails(
        UUID membershipId,
        UUID clientId,
        MembershipStatus status) {
}
