package io.github.guillermodubon.coachgym.membership.domain;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;

public record MembershipRenewal(
        short periodNumber,
        MembershipStatus previousStatus,
        MembershipStatus resultingStatus,
        MembershipPeriodDates dates,
        MembershipPricingSnapshot pricing) {

    public MembershipRenewal {

        if (periodNumber <= 1) {
            throw new MembershipValidationException(
                    "Renewal period number must be greater than one.");
        }

        if (previousStatus == null) {
            throw new MembershipValidationException(
                    "Previous membership status must be provided.");
        }

        if (resultingStatus == null) {
            throw new MembershipValidationException(
                    "Resulting membership status must be provided.");
        }

        if (resultingStatus != MembershipStatus.ACTIVE) {
            throw new MembershipValidationException(
                    "A renewed membership must result in ACTIVE status.");
        }

        if (dates == null) {
            throw new MembershipValidationException(
                    "Renewal period dates must be provided.");
        }

        if (pricing == null) {
            throw new MembershipValidationException(
                    "Renewal pricing snapshot must be provided.");
        }
    }

    public boolean changesMembershipStatus() {
        return previousStatus != resultingStatus;
    }
}
