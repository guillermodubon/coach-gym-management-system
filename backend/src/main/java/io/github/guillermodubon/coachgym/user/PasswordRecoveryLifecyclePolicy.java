package io.github.guillermodubon.coachgym.user;

import java.util.Objects;

/** Allowed one-way state transitions for a one-time recovery request. */
public final class PasswordRecoveryLifecyclePolicy {

    private PasswordRecoveryLifecyclePolicy() {
    }

    public static boolean allowsTransition(PasswordRecoveryStatus current, PasswordRecoveryStatus next) {
        Objects.requireNonNull(current, "Current recovery status is required.");
        Objects.requireNonNull(next, "Next recovery status is required.");
        return current == PasswordRecoveryStatus.PENDING
                && (next == PasswordRecoveryStatus.USED
                        || next == PasswordRecoveryStatus.EXPIRED
                        || next == PasswordRecoveryStatus.REVOKED);
    }

    public static void requireTransition(PasswordRecoveryStatus current, PasswordRecoveryStatus next) {
        if (!allowsTransition(current, next)) {
            throw new StaffIdentityStateConflictException(
                    "Password-recovery lifecycle transition is not allowed.");
        }
    }
}
