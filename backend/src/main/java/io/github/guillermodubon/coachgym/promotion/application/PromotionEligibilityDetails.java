package io.github.guillermodubon.coachgym.promotion.application;

import io.github.guillermodubon.coachgym.promotion.PromotionEligiblePlan;
import java.util.List;
import java.util.UUID;

public record PromotionEligibilityDetails(
        UUID promotionId,
        long promotionVersion,
        List<PromotionEligiblePlan> eligiblePlans) {

    public PromotionEligibilityDetails {
        eligiblePlans =
                List.copyOf(eligiblePlans);
    }
}
