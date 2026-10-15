package io.github.guillermodubon.coachgym.promotion;

/**
 * Auditable changes supported by the promotion catalog.
 */
public enum PromotionChangeType {
    CREATED,
    UPDATED,
    DEACTIVATED,
    REACTIVATED,
    ELIGIBLE_PLANS_CHANGED
}