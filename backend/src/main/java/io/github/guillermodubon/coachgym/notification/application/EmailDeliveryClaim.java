package io.github.guillermodubon.coachgym.notification.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Database-owned lease for one email attempt.
 *
 * <p>The token is intentionally confined to the application boundary. It is
 * never rendered in an API response, log, event, or audit record.</p>
 */
public record EmailDeliveryClaim(
        UUID deliveryId,
        UUID claimToken,
        long expectedVersion,
        Instant claimedAt,
        Instant expiresAt) {

    public EmailDeliveryClaim {
        if (deliveryId == null || claimToken == null) {
            throw new IllegalArgumentException("Email delivery claim identifiers are required.");
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("Email delivery claim version must not be negative.");
        }
        if (claimedAt == null || expiresAt == null || !expiresAt.isAfter(claimedAt)) {
            throw new IllegalArgumentException("Email delivery claim expiry is invalid.");
        }
    }
}
