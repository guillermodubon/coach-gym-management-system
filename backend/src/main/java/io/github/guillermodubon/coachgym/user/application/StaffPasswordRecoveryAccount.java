package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import java.util.UUID;

/** Locked, short-lived account projection; credentials and contact data are redacted. */
public record StaffPasswordRecoveryAccount(
        UUID userId,
        String normalizedEmail,
        String displayName,
        String passwordHash,
        StaffAccountStatus status,
        long securityVersion) {

    @Override
    public String toString() {
        return "StaffPasswordRecoveryAccount[userPresent=" + (userId != null)
                + ", emailPresent=" + (normalizedEmail != null)
                + ", displayNamePresent=" + (displayName != null)
                + ", passwordHash=<redacted>, status=" + status
                + ", securityVersion=" + securityVersion + ']';
    }
}
