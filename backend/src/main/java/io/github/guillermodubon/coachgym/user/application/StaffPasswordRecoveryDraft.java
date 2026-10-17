package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.PasswordRecoveryPolicy;
import java.time.Instant;
import java.util.UUID;

/** Persistence input for one pending recovery request, containing no raw token. */
public record StaffPasswordRecoveryDraft(
        UUID recoveryId,
        UUID userId,
        StaffTokenFingerprint tokenFingerprint,
        Instant requestedAt,
        Instant expiresAt) {

    public StaffPasswordRecoveryDraft {
        if (recoveryId == null || userId == null || tokenFingerprint == null
                || !StaffTokenPurpose.PASSWORD_RECOVERY.schemeVersion()
                        .equals(tokenFingerprint.schemeVersion())) {
            throw new IllegalArgumentException("Password recovery request is invalid.");
        }
        PasswordRecoveryPolicy.requireExpiration(requestedAt, expiresAt);
    }

    @Override
    public String toString() {
        return "StaffPasswordRecoveryDraft[recoveryId=" + recoveryId
                + ", userId=" + userId
                + ", tokenFingerprint=<redacted>"
                + ", requestedAt=" + requestedAt
                + ", expiresAt=" + expiresAt + ']';
    }
}
