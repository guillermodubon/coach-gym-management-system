package io.github.guillermodubon.coachgym.membership;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Event emitted after a frozen membership has been reactivated successfully.
 */
public record MembershipReactivated(
        UUID membershipId,
        String membershipCode,
        UUID clientId,
        UUID membershipPeriodId,
        UUID membershipFreezeId,
        LocalDate freezeStartsOn,
        LocalDate plannedEndsOn,
        LocalDate reactivatedOn,
        String reason,
        MembershipStatus previousStatus,
        MembershipStatus resultingStatus,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public MembershipReactivated {
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

        if (membershipFreezeId == null) {
            throw new IllegalArgumentException(
                    "Membership freeze identifier must be provided.");
        }

        if (freezeStartsOn == null) {
            throw new IllegalArgumentException(
                    "Membership freeze start date must be provided.");
        }

        if (plannedEndsOn == null) {
            throw new IllegalArgumentException(
                    "Membership freeze planned end date must be provided.");
        }

        if (reactivatedOn == null) {
            throw new IllegalArgumentException(
                    "Membership reactivation date must be provided.");
        }

        if (reactivatedOn.isBefore(freezeStartsOn)) {
            throw new IllegalArgumentException(
                    "Membership reactivation date must not be "
                            + "before the freeze start date.");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Membership freeze reason must not be blank.");
        }

        if (previousStatus != MembershipStatus.FROZEN) {
            throw new IllegalArgumentException(
                    "Membership reactivation previous status "
                            + "must be FROZEN.");
        }

        if (resultingStatus != MembershipStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Membership reactivation resulting status "
                            + "must be ACTIVE.");
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
