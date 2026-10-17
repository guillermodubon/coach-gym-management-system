package io.github.guillermodubon.coachgym.promotion;

public class PromotionEvaluationException
        extends RuntimeException {

    private final PromotionEvaluationFailure failure;

    public PromotionEvaluationException(
            PromotionEvaluationFailure failure,
            String message) {

        super(message);
        this.failure = failure;
    }

    public PromotionEvaluationFailure failure() {
        return failure;
    }
}
