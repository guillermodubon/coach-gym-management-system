package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.application.FreezeMembershipCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record FreezeMembershipRequest(
        @NotNull
        LocalDate startsOn,

        @NotNull
        LocalDate plannedEndsOn,

        @NotBlank
        @Size(max = 2_000)
        String reason,

        @NotNull
        @PositiveOrZero
        Long version) {

    FreezeMembershipCommand toCommand() {
        return new FreezeMembershipCommand(
                startsOn,
                plannedEndsOn,
                reason,
                version);
    }
}
