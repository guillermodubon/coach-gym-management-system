package io.github.guillermodubon.coachgym.membership;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Event emitted after a membership has been frozen successfully.
 */
public record MembershipFrozen(
        UUID membershipId,
        String membershipCode,
        UUID clientId,
        UUID membershipPeriodId,
        LocalDate startsOn,
        LocalDate plannedEndsOn,
        String reason,
        MembershipStatus previousStatus,
        MembershipStatus resultingStatus,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public MembershipFrozen {
        if (membershipId == null) {
            throw new IllegalArgumentException(
                    "Membership identifier must be provided.");
        }

        if (membershipCode == null
                || membershipCode.isBlank()) {

            throw new IllegalArgumentException(
                    "Membership code must be provided.");
        }

        if (clientId == null) {
            throw new IllegalArgumentException(
                    "Membership client identifier must be provided.");
        }

        if (membershipPeriodId == null) {
            throw new IllegalArgumentException(
                    "Membership period identifier must be provided.");
        }

        if (startsOn == null) {
            throw new IllegalArgumentException(
                    "Membership freeze start date must be provided.");
        }

        if (plannedEndsOn == null) {
            throw new IllegalArgumentException(
                    "Membership freeze planned end date must be provided.");
        }

        if (!plannedEndsOn.isAfter(startsOn)) {
            throw new IllegalArgumentException(
                    "Membership freeze planned end date must be "
                            + "after its start date.");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Membership freeze reason must not be blank.");
        }

        if (previousStatus != MembershipStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Membership freeze previous status must be ACTIVE.");
        }

        if (resultingStatus != MembershipStatus.FROZEN) {
            throw new IllegalArgumentException(
                    "Membership freeze resulting status must be FROZEN.");
        }

        if (actorUserId == null) {
            throw new IllegalArgumentException(
                    "Actor user identifier must be provided.");
        }

        if (actorIdentifier == null
                || actorIdentifier.isBlank()) {

            throw new IllegalArgumentException(
                    "Actor identifier must be provided.");
        }

        if (occurredAt == null) {
            throw new IllegalArgumentException(
                    "Occurrence time must be provided.");
        }

        membershipCode = membershipCode.trim();
        reason = reason.trim();
        actorIdentifier = actorIdentifier.trim();
    }
}
