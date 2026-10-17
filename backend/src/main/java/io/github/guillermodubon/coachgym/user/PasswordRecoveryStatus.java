package io.github.guillermodubon.coachgym.user;

/** Public lifecycle states of a one-time password-recovery request. */
public enum PasswordRecoveryStatus {
    PENDING,
    USED,
    EXPIRED,
    REVOKED
}
