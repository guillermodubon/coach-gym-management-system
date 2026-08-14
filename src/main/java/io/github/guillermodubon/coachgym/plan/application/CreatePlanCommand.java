package io.github.guillermodubon.coachgym.plan.application;

import io.github.guillermodubon.coachgym.plan.DurationUnit;
import java.math.BigDecimal;

public record CreatePlanCommand(
        String name,
        String description,
        int durationValue,
        DurationUnit durationUnit,
        BigDecimal listPrice,
        String currency) {
}
