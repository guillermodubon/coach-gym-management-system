package io.github.guillermodubon.coachgym.user;

import java.util.Objects;

/** Minimal authoritative account facts used to reject stale authenticated principals. */
public record StaffAccountSecurityState(
        StaffAccountStatus status,
        long securityVersion,
        boolean passwordChangeRequired) {

    public StaffAccountSecurityState {
        status = Objects.requireNonNull(status, "status is required");
        if (securityVersion < 0) {
            throw new IllegalArgumentException("Security version must not be negative.");
        }
    }
}
