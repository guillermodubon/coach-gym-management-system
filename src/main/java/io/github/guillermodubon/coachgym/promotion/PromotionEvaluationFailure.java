package io.github.guillermodubon.coachgym.promotion;

public enum PromotionEvaluationFailure {

    PROMOTION_NOT_FOUND,
    PROMOTION_INACTIVE,
    PROMOTION_NOT_YET_VALID,
    PROMOTION_EXPIRED,
    PLAN_NOT_ELIGIBLE,
    CURRENCY_MISMATCH,
    INVALID_PRICE,
    INVALID_CURRENCY
}
