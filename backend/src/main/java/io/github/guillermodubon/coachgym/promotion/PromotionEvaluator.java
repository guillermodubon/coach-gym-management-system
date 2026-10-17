package io.github.guillermodubon.coachgym.promotion;

/**
 * Public promotion-module boundary used when pricing a membership
 * period.
 */
public interface PromotionEvaluator {

    PromotionEvaluationResult evaluate(
            PromotionEvaluationRequest request
    );
}
