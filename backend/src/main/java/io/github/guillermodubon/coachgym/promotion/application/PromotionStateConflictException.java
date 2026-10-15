package io.github.guillermodubon.coachgym.promotion.application;

public class PromotionStateConflictException
        extends RuntimeException {

    public PromotionStateConflictException(
            boolean currentState) {

        super(
                currentState
                        ? "Promotion is already active."
                        : "Promotion is already inactive.");
    }
}
