package io.github.guillermodubon.coachgym.access.domain;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessResult;

/**
 * The deterministic outcome produced by {@link AccessPolicy}.
 *
 * <p>Consistency invariant (enforced by factory methods):</p>
 * <ul>
 *   <li>{@code result == ALLOWED} iff {@code reasonCode == ACCESS_ALLOWED}</li>
 *   <li>{@code result == DENIED} iff {@code reasonCode} is one of the eight
 *       denial codes</li>
 * </ul>
 */
public record AccessEvaluation(
        AccessResult result,
        AccessReasonCode reasonCode,
        String reason) {

    /** Creates an ALLOWED evaluation. */
    public static AccessEvaluation allowed() {
        return new AccessEvaluation(
                AccessResult.ALLOWED,
                AccessReasonCode.ACCESS_ALLOWED,
                "Membership is active and its current period is valid.");
    }

    /** Creates a DENIED evaluation with the supplied code and message. */
    public static AccessEvaluation denied(
            AccessReasonCode reasonCode,
            String reason) {

        if (reasonCode == null) {
            throw new IllegalArgumentException(
                    "Denial reason code must be provided.");
        }
        if (reasonCode == AccessReasonCode.ACCESS_ALLOWED) {
            throw new IllegalArgumentException(
                    "ACCESS_ALLOWED is not a valid denial reason code.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Denial reason text must be provided.");
        }

        return new AccessEvaluation(
                AccessResult.DENIED,
                reasonCode,
                reason);
    }
}
