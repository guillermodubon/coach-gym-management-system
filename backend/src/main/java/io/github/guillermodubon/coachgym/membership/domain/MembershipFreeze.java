package io.github.guillermodubon.coachgym.membership.domain;

import java.time.LocalDate;
import java.util.UUID;

public record MembershipFreeze(
        UUID membershipId,
        UUID membershipPeriodId,
        LocalDate startsOn,
        LocalDate plannedEndsOn,
        String reason) {

    private static final int MAX_REASON_LENGTH = 2_000;

    public MembershipFreeze {
        if (membershipId == null) {
            throw new MembershipValidationException(
                    "Membership identifier must be provided.");
        }

        if (membershipPeriodId == null) {
            throw new MembershipValidationException(
                    "Membership period identifier must be provided.");
        }

        if (startsOn == null) {
            throw new MembershipValidationException(
                    "Membership freeze start date must be provided.");
        }

        if (plannedEndsOn == null) {
            throw new MembershipValidationException(
                    "Membership freeze planned end date must be provided.");
        }

        if (!plannedEndsOn.isAfter(startsOn)) {
            throw new MembershipValidationException(
                    "Membership freeze planned end date must be "
                            + "after its start date.");
        }

        if (reason == null || reason.isBlank()) {
            throw new MembershipValidationException(
                    "Membership freeze reason must not be blank.");
        }

        String normalizedReason = reason.trim();

        if (normalizedReason.length() > MAX_REASON_LENGTH) {
            throw new MembershipValidationException(
                    "Membership freeze reason must not exceed "
                            + MAX_REASON_LENGTH
                            + " characters.");
        }

        reason = normalizedReason;
    }
}

