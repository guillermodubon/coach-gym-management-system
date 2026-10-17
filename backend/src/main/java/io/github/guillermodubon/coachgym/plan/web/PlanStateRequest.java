package io.github.guillermodubon.coachgym.plan.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PlanStateRequest(@NotNull @PositiveOrZero Long version) {
}
