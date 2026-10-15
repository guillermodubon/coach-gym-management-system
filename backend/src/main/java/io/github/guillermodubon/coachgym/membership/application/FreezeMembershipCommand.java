package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.membership.domain.MembershipValidationException;
import java.time.LocalDate;

public record FreezeMembershipCommand(
        LocalDate startsOn,
        LocalDate plannedEndsOn,
        String reason,
        long version) {

    private static final int MAX_REASON_LENGTH = 2_000;

    public FreezeMembershipCommand {
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

        if (version < 0) {
            throw new MembershipValidationException(
                    "Membership version must not be negative.");
        }

        reason = normalizedReason;
    }
}
