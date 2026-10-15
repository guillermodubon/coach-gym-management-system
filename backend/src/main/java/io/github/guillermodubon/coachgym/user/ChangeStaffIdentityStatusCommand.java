package io.github.guillermodubon.coachgym.user;

import java.util.Objects;
import java.util.UUID;

/** Organization-administrator request to change another account's lifecycle state. */
public record ChangeStaffIdentityStatusCommand(
        UUID targetUserId,
        StaffIdentityStatus requestedStatus,
        String reason,
        long expectedVersion) {

    public ChangeStaffIdentityStatusCommand {
        if (targetUserId == null) {
            throw new StaffIdentityValidationException("Target staff identity is required.");
        }
        requestedStatus = Objects.requireNonNull(
                requestedStatus, "Requested identity status is required.");
        if (requestedStatus == StaffIdentityStatus.INVITED) {
            throw new StaffIdentityValidationException(
                    "Invitation status cannot be changed through account lifecycle administration.");
        }
        reason = StaffIdentityValuePolicy.requireReason(reason);
        if (expectedVersion < 0) {
            throw new StaffIdentityValidationException("Expected account version must not be negative.");
        }
    }

    @Override
    public String toString() {
        return "ChangeStaffIdentityStatusCommand[requestedStatus=" + requestedStatus
                + ", reasonPresent=" + (reason != null)
                + ", expectedVersion=" + expectedVersion
                + ']';
    }
}
