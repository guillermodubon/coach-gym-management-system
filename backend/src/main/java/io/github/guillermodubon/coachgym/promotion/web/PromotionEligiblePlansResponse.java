package io.github.guillermodubon.coachgym.promotion.web;

import io.github.guillermodubon.coachgym.promotion.application.PromotionEligibilityDetails;
import java.util.List;
import java.util.UUID;

public record PromotionEligiblePlansResponse(
        UUID promotionId,
        long promotionVersion,
        List<PromotionEligiblePlanResponse> items) {

    public PromotionEligiblePlansResponse {
        items = List.copyOf(items);
    }

    static PromotionEligiblePlansResponse from(
            PromotionEligibilityDetails eligibility) {

        return new PromotionEligiblePlansResponse(
                eligibility.promotionId(),
                eligibility.promotionVersion(),
                eligibility.eligiblePlans()
                        .stream()
                        .map(
                                PromotionEligiblePlanResponse::from)
                        .toList());
    }
}
