package io.github.guillermodubon.coachgym.configuration;

import java.time.Instant;
import java.util.UUID;

/**
 * Privacy-safe event emitted after the global access-payment policy changes.
 *
 * <p>The settings row is a singleton and does not have a UUID identity. The
 * stable logical identifier is provided for audit resource correlation without
 * exposing persistence details or payment information.</p>
 */
public record AccessPaymentPolicyChanged(
        boolean previousValue,
        boolean newValue,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    /** Stable logical resource identifier for the singleton settings row. */
    public static final UUID SETTINGS_RESOURCE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    public AccessPaymentPolicyChanged {
        if (previousValue == newValue) {
            throw new IllegalArgumentException(
                    "Access payment policy event must represent a change.");
        }
        if (actorUserId == null) {
            throw new IllegalArgumentException(
                    "Access payment policy event actor is required.");
        }
        if (actorIdentifier == null || actorIdentifier.isBlank()) {
            throw new IllegalArgumentException(
                    "Access payment policy event actor identifier is required.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException(
                    "Access payment policy event timestamp is required.");
        }
        actorIdentifier = actorIdentifier.strip();
    }
}
