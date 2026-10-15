package io.github.guillermodubon.coachgym.access.domain;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import java.util.Collection;

/**
 * Pure tie-breaker for independent access rules that produce competing
 * denials. It does not resolve identity, authorize staff, or perform I/O.
 */
public final class AccessDenialPrecedence {

    private AccessDenialPrecedence() {
    }

    /**
     * Selects the highest-priority denial. An empty set of denial candidates
     * means no denial and returns {@link AccessReasonCode#ACCESS_ALLOWED}.
     * Passing {@code ACCESS_ALLOWED} or {@code null} as a denial candidate is
     * a programming error.
     */
    public static AccessReasonCode selectHighestPriority(
            Collection<AccessReasonCode> denialCandidates) {

        if (denialCandidates == null) {
            throw new IllegalArgumentException(
                    "Access denial candidates must be provided.");
        }

        AccessReasonCode selected = AccessReasonCode.ACCESS_ALLOWED;
        int selectedPriority = Integer.MAX_VALUE;
        for (AccessReasonCode candidate : denialCandidates) {
            if (candidate == null) {
                throw new IllegalArgumentException(
                        "Access denial candidates cannot contain null.");
            }
            int priority = priority(candidate);
            if (priority < selectedPriority) {
                selected = candidate;
                selectedPriority = priority;
            }
        }
        return selected;
    }

    private static int priority(AccessReasonCode reasonCode) {
        return switch (reasonCode) {
            // QR credential resolution is an upstream gate. A recent
            // successful QR entry also retains its established precedence
            // over membership and payment outcomes.
            case ACCESS_CREDENTIAL_INVALID -> 0;
            case DUPLICATE_CHECK_IN -> 1;

            // Preserve AccessPolicy's existing privacy-safe order.
            case IDENTIFIER_NOT_FOUND -> 2;
            case CLIENT_INACTIVE -> 3;
            case MEMBERSHIP_NOT_FOUND -> 4;
            case MEMBERSHIP_CANCELLED -> 5;
            case MEMBERSHIP_FROZEN -> 6;
            case MEMBERSHIP_EXPIRED -> 7;
            case MEMBERSHIP_PERIOD_EXPIRED -> 8;
            case MEMBERSHIP_NOT_STARTED -> 9;

            // Do not reveal branch entitlement before the membership itself
            // has passed its existing lifecycle and date checks.
            case MEMBERSHIP_NOT_VALID_AT_BRANCH -> 10;
            case PAYMENT_REQUIRED -> 11;
            case ACCESS_ALLOWED -> throw new IllegalArgumentException(
                    "ACCESS_ALLOWED is not a denial candidate.");
        };
    }
}
