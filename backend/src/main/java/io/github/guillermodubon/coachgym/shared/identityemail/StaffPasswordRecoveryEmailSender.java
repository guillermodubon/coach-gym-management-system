package io.github.guillermodubon.coachgym.shared.identityemail;

/** Provider-neutral ephemeral boundary for a one-time password recovery link. */
public interface StaffPasswordRecoveryEmailSender {

    /** Queues a best-effort post-commit send; implementations must not persist token-bearing content. */
    void sendPasswordRecoveryLink(StaffPasswordRecoveryEmail email);
}
