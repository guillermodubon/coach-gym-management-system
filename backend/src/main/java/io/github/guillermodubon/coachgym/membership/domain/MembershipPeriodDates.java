package io.github.guillermodubon.coachgym.membership.domain;

import java.time.LocalDate;

public record MembershipPeriodDates(
        LocalDate startsOn,
        LocalDate baseEndsOn,
        LocalDate effectiveEndsOn) {

    public MembershipPeriodDates {

        if (startsOn == null) {
            throw new MembershipValidationException(
                    "Membership period start date must be provided.");
        }

        if (baseEndsOn == null) {
            throw new MembershipValidationException(
                    "Membership period base end date must be provided.");
        }

        if (effectiveEndsOn == null) {
            throw new MembershipValidationException(
                    "Membership period effective end date "
                            + "must be provided.");
        }

        if (!baseEndsOn.isAfter(startsOn)) {
            throw new MembershipValidationException(
                    "Membership period base end date must be "
                            + "after its start date.");
        }

        if (effectiveEndsOn.isBefore(baseEndsOn)) {
            throw new MembershipValidationException(
                    "Membership period effective end date "
                            + "must not be before its base end date.");
        }
    }
}