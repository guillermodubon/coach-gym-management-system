package io.github.guillermodubon.coachgym.membership.domain;

import io.github.guillermodubon.coachgym.membership.MembershipFreezeDetails;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.application.MembershipAlreadyFrozenException;
import io.github.guillermodubon.coachgym.membership.application.MembershipFreezeStateConflictException;
import io.github.guillermodubon.coachgym.membership.application.MembershipNotFrozenException;
import java.time.LocalDate;
import java.util.UUID;

public final class MembershipFreezePolicy {

    private MembershipFreezePolicy() {
    }

    public static MembershipFreeze createFreeze(
            UUID membershipId,
            UUID membershipPeriodId,
            MembershipStatus currentStatus,
            boolean hasOpenFreeze,
            LocalDate startsOn,
            LocalDate plannedEndsOn,
            String reason) {

        validateMembershipIdentifier(membershipId);
        validateMembershipPeriodIdentifier(membershipPeriodId);
        validateStatus(currentStatus);

        ensureCanFreeze(
                membershipId,
                currentStatus,
                hasOpenFreeze);

        return new MembershipFreeze(
                membershipId,
                membershipPeriodId,
                startsOn,
                plannedEndsOn,
                reason);
    }

    public static void validateReactivation(
            UUID membershipId,
            MembershipStatus currentStatus,
            MembershipFreezeDetails openFreeze,
            LocalDate reactivatedOn) {

        validateMembershipIdentifier(membershipId);
        validateStatus(currentStatus);

        if (reactivatedOn == null) {
            throw new MembershipValidationException(
                    "Membership reactivation date must be provided.");
        }

        if (currentStatus != MembershipStatus.FROZEN) {
            throw new MembershipNotFrozenException(
                    membershipId,
                    currentStatus);
        }

        if (openFreeze == null || !openFreeze.open()) {
            throw new MembershipNotFrozenException(
                    membershipId,
                    currentStatus);
        }

        if (!membershipId.equals(openFreeze.membershipId())) {
            throw new MembershipValidationException(
                    "Open membership freeze does not belong "
                            + "to the requested membership.");
        }

        if (reactivatedOn.isBefore(openFreeze.startsOn())) {
            throw new MembershipValidationException(
                    "Membership reactivation date must not be "
                            + "before the freeze start date.");
        }
    }

    private static void ensureCanFreeze(
            UUID membershipId,
            MembershipStatus currentStatus,
            boolean hasOpenFreeze) {

        if (currentStatus == MembershipStatus.FROZEN
                || hasOpenFreeze) {

            throw new MembershipAlreadyFrozenException(
                    membershipId);
        }

        if (currentStatus != MembershipStatus.ACTIVE) {
            throw new MembershipFreezeStateConflictException(
                    membershipId,
                    currentStatus);
        }
    }

    private static void validateMembershipIdentifier(
            UUID membershipId) {

        if (membershipId == null) {
            throw new MembershipValidationException(
                    "Membership identifier must be provided.");
        }
    }

    private static void validateMembershipPeriodIdentifier(
            UUID membershipPeriodId) {

        if (membershipPeriodId == null) {
            throw new MembershipValidationException(
                    "Membership period identifier must be provided.");
        }
    }

    private static void validateStatus(
            MembershipStatus status) {

        if (status == null) {
            throw new MembershipValidationException(
                    "Membership status must be provided.");
        }
    }
}
