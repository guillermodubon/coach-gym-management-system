package io.github.guillermodubon.coachgym.plan.web;

import io.github.guillermodubon.coachgym.plan.DurationUnit;
import io.github.guillermodubon.coachgym.plan.PlanDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        String planCode,
        String name,
        String description,
        int durationValue,
        DurationUnit durationUnit,
        BigDecimal listPrice,
        String currency,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static PlanResponse from(PlanDetails plan) {
        return new PlanResponse(
                plan.id(),
                plan.planCode(),
                plan.name(),
                plan.description(),
                plan.durationValue(),
                plan.durationUnit(),
                plan.listPrice(),
                plan.currency(),
                plan.active(),
                plan.createdAt(),
                plan.updatedAt(),
                plan.version());
    }
}
