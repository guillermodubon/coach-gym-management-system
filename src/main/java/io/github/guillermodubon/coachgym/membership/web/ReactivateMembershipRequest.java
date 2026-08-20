package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.application.ReactivateMembershipCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public record ReactivateMembershipRequest(
        @NotNull
        LocalDate reactivatedOn,

        @NotNull
        @PositiveOrZero
        Long version) {

    ReactivateMembershipCommand toCommand() {
        return new ReactivateMembershipCommand(
                reactivatedOn,
                version);
    }
}
