package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.application.CreateMembershipCommand;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateMembershipRequest(
        @NotNull
        UUID clientId,

        @NotNull
        UUID membershipPlanId,

        UUID promotionId,

        @NotNull
        LocalDate startsOn) {

    CreateMembershipCommand toCommand() {
        return new CreateMembershipCommand(
                clientId,
                membershipPlanId,
                promotionId,
                startsOn);
    }
}
