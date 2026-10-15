package io.github.guillermodubon.coachgym.promotion;

import java.util.UUID;

public record PromotionEligiblePlan(
        UUID planId,
        String planCode,
        String planName,
        boolean active) {
}
