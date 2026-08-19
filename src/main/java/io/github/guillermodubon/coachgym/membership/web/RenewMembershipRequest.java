package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.application.RenewMembershipCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.UUID;

public record RenewMembershipRequest(
        @NotNull
        UUID membershipPlanId,

        UUID promotionId,

        LocalDate startsOn,

        @NotNull
        @PositiveOrZero
        Long version) {

    RenewMembershipCommand toCommand() {

        return new RenewMembershipCommand(
                membershipPlanId,
                promotionId,
                startsOn,
                version);
    }
}
