package io.github.guillermodubon.coachgym.user;

import java.util.Objects;
import java.util.UUID;

/** Allowed lifecycle transitions for a provisioned staff identity. */
public final class StaffIdentityLifecyclePolicy {

    private StaffIdentityLifecyclePolicy() {
    }

    public static boolean allowsTransition(StaffIdentityStatus current, StaffIdentityStatus next) {
        Objects.requireNonNull(current, "Current identity status is required.");
        Objects.requireNonNull(next, "Next identity status is required.");
        return switch (current) {
            case INVITED -> next == StaffIdentityStatus.ACTIVE;
            case ACTIVE -> next == StaffIdentityStatus.SUSPENDED
                    || next == StaffIdentityStatus.DEACTIVATED;
            case SUSPENDED -> next == StaffIdentityStatus.ACTIVE
                    || next == StaffIdentityStatus.DEACTIVATED;
            case DEACTIVATED -> false;
        };
    }

    public static void requireTransition(StaffIdentityStatus current, StaffIdentityStatus next) {
        if (!allowsTransition(current, next)) {
            throw new StaffIdentityStateConflictException(
                    "Staff identity lifecycle transition is not allowed.");
        }
    }

    /** Administration never trusts a caller-supplied target as its actor. */
    public static void requireDifferentActorAndTarget(UUID actorUserId, UUID targetUserId) {
        if (actorUserId == null || targetUserId == null) {
            throw new StaffIdentityValidationException("Actor and target identities are required.");
        }
        if (actorUserId.equals(targetUserId)) {
            throw new StaffIdentityAuthorizationException(
                    "Staff identity administration cannot target the acting account.");
        }
    }

    public static boolean requiresReauthentication(
            StaffIdentityStatus requestedStatus,
            boolean targetIsAdministrator,
            boolean roleOrScopeChanges) {
        Objects.requireNonNull(requestedStatus, "Requested status is required.");
        return (roleOrScopeChanges && requestedStatus != StaffIdentityStatus.INVITED)
                || (targetIsAdministrator
                        && (requestedStatus == StaffIdentityStatus.SUSPENDED
                                || requestedStatus == StaffIdentityStatus.DEACTIVATED));
    }
}
