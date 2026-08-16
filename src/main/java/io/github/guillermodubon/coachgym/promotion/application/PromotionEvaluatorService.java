package io.github.guillermodubon.coachgym.promotion.application;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import io.github.guillermodubon.coachgym.promotion.PromotionDetails;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationException;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationFailure;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationRequest;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationResult;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionEvaluatorService
        implements PromotionEvaluator {

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    private static final int MONEY_SCALE = 2;

    private final PromotionStore promotionStore;
    private final PromotionEligibilityStore eligibilityStore;

    public PromotionEvaluatorService(
            PromotionStore promotionStore,
            PromotionEligibilityStore eligibilityStore) {

        this.promotionStore = promotionStore;
        this.eligibilityStore = eligibilityStore;
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionEvaluationResult evaluate(
            PromotionEvaluationRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Promotion evaluation request must be provided.");
        }

        PromotionDetails promotion =
                promotionStore.findById(
                                request.promotionId())
                        .orElseThrow(
                                () ->
                                        new PromotionEvaluationException(
                                                PromotionEvaluationFailure
                                                        .PROMOTION_NOT_FOUND,
                                                "Promotion "
                                                        + request.promotionId()
                                                        + " was not found."));

        validatePromotionState(
                promotion,
                request);

        validatePlanEligibility(
                promotion,
                request);

        BigDecimal listPrice =
                normalizeMoney(
                        request.listPrice());

        BigDecimal discountAmount =
                calculateDiscount(
                        promotion,
                        listPrice,
                        request.currency());

        BigDecimal cappedDiscount =
                discountAmount.min(listPrice);

        BigDecimal finalPrice =
                listPrice.subtract(
                        cappedDiscount);

        return new PromotionEvaluationResult(
                promotion.id(),
                promotion.promotionCode(),
                promotion.name(),
                promotion.discountType(),
                promotion.discountValue(),
                promotion.currency(),
                listPrice,
                request.currency(),
                cappedDiscount,
                finalPrice);
    }

    private static void validatePromotionState(
            PromotionDetails promotion,
            PromotionEvaluationRequest request) {

        if (!promotion.active()) {
            throw new PromotionEvaluationException(
                    PromotionEvaluationFailure
                            .PROMOTION_INACTIVE,
                    "Promotion is inactive.");
        }

        if (request.applicableOn()
                .isBefore(
                        promotion.validFrom())) {

            throw new PromotionEvaluationException(
                    PromotionEvaluationFailure
                            .PROMOTION_NOT_YET_VALID,
                    "Promotion is not valid yet.");
        }

        if (request.applicableOn()
                .isAfter(
                        promotion.validUntil())) {

            throw new PromotionEvaluationException(
                    PromotionEvaluationFailure
                            .PROMOTION_EXPIRED,
                    "Promotion has expired.");
        }
    }

    private void validatePlanEligibility(
            PromotionDetails promotion,
            PromotionEvaluationRequest request) {

        boolean eligible =
                eligibilityStore.isPlanEligible(
                        promotion.id(),
                        request.membershipPlanId());

        if (!eligible) {
            throw new PromotionEvaluationException(
                    PromotionEvaluationFailure
                            .PLAN_NOT_ELIGIBLE,
                    "Promotion is not eligible for membership plan "
                            + request.membershipPlanId()
                            + ".");
        }
    }

    private static BigDecimal calculateDiscount(
            PromotionDetails promotion,
            BigDecimal listPrice,
            String membershipCurrency) {

        if (promotion.discountType()
                == DiscountType.PERCENTAGE) {

            return listPrice
                    .multiply(
                            promotion.discountValue())
                    .divide(
                            ONE_HUNDRED,
                            MONEY_SCALE,
                            RoundingMode.HALF_UP);
        }

        validateFixedAmountCurrency(
                promotion,
                membershipCurrency);

        return normalizeMoney(
                promotion.discountValue());
    }

    private static void validateFixedAmountCurrency(
            PromotionDetails promotion,
            String membershipCurrency) {

        if (!promotion.currency()
                .equals(membershipCurrency)) {

            throw new PromotionEvaluationException(
                    PromotionEvaluationFailure
                            .CURRENCY_MISMATCH,
                    "Fixed amount promotion currency "
                            + promotion.currency()
                            + " does not match membership plan currency "
                            + membershipCurrency
                            + ".");
        }
    }

    private static BigDecimal normalizeMoney(
            BigDecimal value) {

        return value.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP);
    }
}
