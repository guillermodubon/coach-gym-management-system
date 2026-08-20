package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.membership.domain.MembershipValidationException;
import java.time.LocalDate;

public record ReactivateMembershipCommand(
        LocalDate reactivatedOn,
        long version) {

    public ReactivateMembershipCommand {
        if (reactivatedOn == null) {
            throw new MembershipValidationException(
                    "Membership reactivation date must be provided.");
        }

        if (version < 0) {
            throw new MembershipValidationException(
                    "Membership version must not be negative.");
        }
    }
}