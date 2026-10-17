package io.github.guillermodubon.coachgym.membership.domain;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import java.time.LocalDate;
import java.util.UUID;

public record MembershipCancellation(
        UUID membershipId,
        UUID membershipPeriodId,
        LocalDate cancelledOn,
        String reason,
        MembershipStatus previousStatus) {

    private static final int MAX_REASON_LENGTH =
            2_000;

    public MembershipCancellation {
        if (membershipId == null) {
            throw new MembershipValidationException(
                    "Membership identifier must be provided.");
        }

        if (membershipPeriodId == null) {
            throw new MembershipValidationException(
                    "Membership period identifier must be provided.");
        }

        if (cancelledOn == null) {
            throw new MembershipValidationException(
                    "Membership cancellation date must be provided.");
        }

        if (reason == null || reason.isBlank()) {
            throw new MembershipValidationException(
                    "Membership cancellation reason must not be blank.");
        }

        String normalizedReason =
                reason.trim();

        if (normalizedReason.length()
                > MAX_REASON_LENGTH) {

            throw new MembershipValidationException(
                    "Membership cancellation reason must not exceed "
                            + MAX_REASON_LENGTH
                            + " characters.");
        }

        if (previousStatus == null) {
            throw new MembershipValidationException(
                    "Previous membership status must be provided.");
        }

        if (previousStatus != MembershipStatus.ACTIVE
                && previousStatus != MembershipStatus.FROZEN) {

            throw new MembershipValidationException(
                    "Membership cancellation previous status must be "
                            + "ACTIVE or FROZEN.");
        }

        reason =
                normalizedReason;
    }

    public MembershipStatus resultingStatus() {
        return MembershipStatus.CANCELLED;
    }

    public boolean closesOpenFreeze() {
        return previousStatus
                == MembershipStatus.FROZEN;
    }
}
