package io.github.guillermodubon.coachgym.promotion.web;

import io.github.guillermodubon.coachgym.promotion.application.ReplaceEligiblePlansCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record ReplaceEligiblePlansRequest(
        @NotNull
        @Size(max = 100)
        Set<@NotNull UUID> planIds,

        @NotNull
        @PositiveOrZero
        Long promotionVersion) {

    ReplaceEligiblePlansCommand toCommand() {
        return new ReplaceEligiblePlansCommand(
                planIds,
                promotionVersion);
    }
}
