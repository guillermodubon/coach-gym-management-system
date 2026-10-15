package io.github.guillermodubon.coachgym.membership.domain;

import java.util.UUID;

public record MembershipCreation(
        UUID clientId,
        MembershipPeriodDates dates,
        MembershipPricingSnapshot pricing) {

    public MembershipCreation {

        if (clientId == null) {
            throw new MembershipValidationException(
                    "Membership client identifier must be provided.");
        }

        if (dates == null) {
            throw new MembershipValidationException(
                    "Membership period dates must be provided.");
        }

        if (pricing == null) {
            throw new MembershipValidationException(
                    "Membership pricing snapshot must be provided.");
        }
    }
}