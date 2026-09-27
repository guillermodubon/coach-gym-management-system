package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.PasswordRecoveryStatus;
import java.time.Instant;
import java.util.UUID;

/** Internal persistence projection with token material omitted from diagnostics. */
public record StaffPasswordRecoveryRecord(
        UUID recoveryId,
        UUID userId,
        StaffTokenFingerprint tokenFingerprint,
        PasswordRecoveryStatus status,
        Instant requestedAt,
        Instant expiresAt,
        Instant usedAt,
        Instant expiredAt,
        Instant revokedAt,
        int failedAttemptCount,
        long version) {

    @Override
    public String toString() {
        return "StaffPasswordRecoveryRecord[recoveryId=" + recoveryId
                + ", userId=" + userId
                + ", tokenFingerprint=<redacted>"
                + ", status=" + status
                + ", requestedAt=" + requestedAt
                + ", expiresAt=" + expiresAt
                + ", usedAt=" + usedAt
                + ", expiredAt=" + expiredAt
                + ", revokedAt=" + revokedAt
                + ", failedAttemptCount=" + failedAttemptCount
                + ", version=" + version + ']';
    }
}
