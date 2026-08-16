package io.github.guillermodubon.coachgym.promotion.web;

import io.github.guillermodubon.coachgym.promotion.PromotionEligiblePlan;
import java.util.UUID;

public record PromotionEligiblePlanResponse(
        UUID planId,
        String planCode,
        String planName,
        boolean active) {

    static PromotionEligiblePlanResponse from(
            PromotionEligiblePlan plan) {

        return new PromotionEligiblePlanResponse(
                plan.planId(),
                plan.planCode(),
                plan.planName(),
                plan.active());
    }
}

