package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.membership.domain.MembershipValidationException;
import java.time.LocalDate;
import java.util.UUID;

public record RenewMembershipCommand(
        UUID membershipPlanId,
        UUID promotionId,
        LocalDate startsOn,
        long version) {

    public RenewMembershipCommand {

        if (membershipPlanId == null) {
            throw new MembershipValidationException(
                    "Renewal membership plan identifier must be provided.");
        }

        if (version < 0) {
            throw new MembershipValidationException(
                    "Membership version must not be negative.");
        }
    }
}
