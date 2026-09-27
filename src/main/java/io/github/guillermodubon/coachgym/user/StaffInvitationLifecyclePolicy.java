package io.github.guillermodubon.coachgym.user;

import java.util.Objects;

/** Allowed one-way state transitions for an invitation. */
public final class StaffInvitationLifecyclePolicy {

    private StaffInvitationLifecyclePolicy() {
    }

    public static boolean allowsTransition(StaffInvitationStatus current, StaffInvitationStatus next) {
        Objects.requireNonNull(current, "Current invitation status is required.");
        Objects.requireNonNull(next, "Next invitation status is required.");
        return current == StaffInvitationStatus.PENDING
                && (next == StaffInvitationStatus.ACCEPTED
                        || next == StaffInvitationStatus.EXPIRED
                        || next == StaffInvitationStatus.REVOKED);
    }

    public static void requireTransition(StaffInvitationStatus current, StaffInvitationStatus next) {
        if (!allowsTransition(current, next)) {
            throw new StaffIdentityStateConflictException(
                    "Invitation lifecycle transition is not allowed.");
        }
    }
}
