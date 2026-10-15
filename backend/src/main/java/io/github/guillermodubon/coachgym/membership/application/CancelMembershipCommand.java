package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.membership.domain.MembershipValidationException;
import java.time.LocalDate;

public record CancelMembershipCommand(
        LocalDate cancelledOn,
        String reason,
        long version) {

    private static final int MAX_REASON_LENGTH =
            2_000;

    public CancelMembershipCommand {
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

        if (version < 0) {
            throw new MembershipValidationException(
                    "Membership version must not be negative.");
        }

        reason =
                normalizedReason;
    }
}
