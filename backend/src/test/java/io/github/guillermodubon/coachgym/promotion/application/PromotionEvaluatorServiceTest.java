package io.github.guillermodubon.coachgym.promotion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import io.github.guillermodubon.coachgym.promotion.PromotionDetails;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationException;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationFailure;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationRequest;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PromotionEvaluatorServiceTest {

    private static final UUID PROMOTION_ID =
            UUID.fromString(
                    "302e448e-6004-4118-a53d-3f552f48c3d1");

    private static final UUID PLAN_ID =
            UUID.fromString(
                    "31ff8e84-f2f8-4650-91cc-88342b57d841");

    private static final LocalDate VALID_FROM =
            LocalDate.of(2026, 9, 1);

    private static final LocalDate VALID_UNTIL =
            LocalDate.of(2026, 9, 30);

    private static final LocalDate APPLICABLE_ON =
            LocalDate.of(2026, 9, 15);

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-15T20:00:00Z");

    @Mock
    private PromotionStore promotionStore;

    @Mock
    private PromotionEligibilityStore eligibilityStore;

    private PromotionEvaluatorService service;

    @BeforeEach
    void setUp() {
        service =
                new PromotionEvaluatorService(
                        promotionStore,
                        eligibilityStore);
    }

    @Test
    void evaluatesPercentagePromotion() {
        PromotionDetails promotion =
                percentagePromotion(
                        true,
                        VALID_FROM,
                        VALID_UNTIL,
                        "10.00");

        prepareEligiblePromotion(
                promotion);

        PromotionEvaluationResult result =
                service.evaluate(
                        request(
                                "25.00",
                                "USD",
                                APPLICABLE_ON));

        assertThat(result.promotionId())
                .isEqualTo(PROMOTION_ID);

        assertThat(result.promotionCode())
                .isEqualTo("PROMO-000001");

        assertThat(result.promotionName())
                .isEqualTo("September Discount");

        assertThat(result.discountType())
                .isEqualTo(
                        DiscountType.PERCENTAGE);

        assertThat(result.discountValue())
                .isEqualByComparingTo("10.00");

        assertThat(result.promotionCurrency())
                .isNull();

        assertThat(result.listPrice())
                .isEqualByComparingTo("25.00");

        assertThat(result.currency())
                .isEqualTo("USD");

        assertThat(result.discountAmount())
                .isEqualByComparingTo("2.50");

        assertThat(result.finalPrice())
                .isEqualByComparingTo("22.50");

        verify(promotionStore)
                .findById(PROMOTION_ID);

        verify(eligibilityStore)
                .isPlanEligible(
                        PROMOTION_ID,
                        PLAN_ID);
    }

    @Test
    void roundsPercentageDiscountToTwoDecimals() {
        PromotionDetails promotion =
                percentagePromotion(
                        true,
                        VALID_FROM,
                        VALID_UNTIL,
                        "15.00");

        prepareEligiblePromotion(
                promotion);

        PromotionEvaluationResult result =
                service.evaluate(
                        request(
                                "19.99",
                                "USD",
                                APPLICABLE_ON));

        assertThat(result.listPrice())
                .isEqualByComparingTo("19.99");

        assertThat(result.discountAmount())
                .isEqualByComparingTo("3.00");

        assertThat(result.finalPrice())
                .isEqualByComparingTo("16.99");
    }

    @Test
    void evaluatesFixedAmountPromotion() {
        PromotionDetails promotion =
                fixedAmountPromotion(
                        true,
                        VALID_FROM,
                        VALID_UNTIL,
                        "5.00",
                        "USD");

        prepareEligiblePromotion(
                promotion);

        PromotionEvaluationResult result =
                service.evaluate(
                        request(
                                "25.00",
                                "usd",
                                APPLICABLE_ON));

        assertThat(result.discountType())
                .isEqualTo(
                        DiscountType.FIXED_AMOUNT);

        assertThat(result.discountValue())
                .isEqualByComparingTo("5.00");

        assertThat(result.promotionCurrency())
                .isEqualTo("USD");

        assertThat(result.currency())
                .isEqualTo("USD");

        assertThat(result.discountAmount())
                .isEqualByComparingTo("5.00");

        assertThat(result.finalPrice())
                .isEqualByComparingTo("20.00");
    }

    @Test
    void capsFixedDiscountAtListPrice() {
        PromotionDetails promotion =
                fixedAmountPromotion(
                        true,
                        VALID_FROM,
                        VALID_UNTIL,
                        "15.00",
                        "USD");

        prepareEligiblePromotion(
                promotion);

        PromotionEvaluationResult result =
                service.evaluate(
                        request(
                                "10.00",
                                "USD",
                                APPLICABLE_ON));

        assertThat(result.discountAmount())
                .isEqualByComparingTo("10.00");

        assertThat(result.finalPrice())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void evaluatesPromotionOnFirstValidityDate() {
        PromotionDetails promotion =
                percentagePromotion(
                        true,
                        VALID_FROM,
                        VALID_UNTIL,
                        "10.00");

        prepareEligiblePromotion(
                promotion);

        PromotionEvaluationResult result =
                service.evaluate(
                        request(
                                "25.00",
                                "USD",
                                VALID_FROM));

        assertThat(result.discountAmount())
                .isEqualByComparingTo("2.50");

        assertThat(result.finalPrice())
                .isEqualByComparingTo("22.50");
    }

    @Test
    void evaluatesPromotionOnLastValidityDate() {
        PromotionDetails promotion =
                percentagePromotion(
                        true,
                        VALID_FROM,
                        VALID_UNTIL,
                        "10.00");

        prepareEligiblePromotion(
                promotion);

        PromotionEvaluationResult result =
                service.evaluate(
                        request(
                                "25.00",
                                "USD",
                                VALID_UNTIL));

        assertThat(result.discountAmount())
                .isEqualByComparingTo("2.50");

        assertThat(result.finalPrice())
                .isEqualByComparingTo("22.50");
    }

    @Test
    void rejectsUnknownPromotion() {
        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () ->
                        service.evaluate(
                                request(
                                        "25.00",
                                        "USD",
                                        APPLICABLE_ON)))
                .isInstanceOf(
                        PromotionEvaluationException.class)
                .satisfies(
                        exception ->
                                assertThat(
                                        ((PromotionEvaluationException)
                                                exception)
                                                .failure())
                                        .isEqualTo(
                                                PromotionEvaluationFailure
                                                        .PROMOTION_NOT_FOUND))
                .hasMessage(
                        "Promotion "
                                + PROMOTION_ID
                                + " was not found.");

        verify(eligibilityStore, never())
                .isPlanEligible(
                        PROMOTION_ID,
                        PLAN_ID);
    }

    @Test
    void rejectsInactivePromotion() {
        PromotionDetails promotion =
                percentagePromotion(
                        false,
                        VALID_FROM,
                        VALID_UNTIL,
                        "10.00");

        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(
                        Optional.of(promotion));

        assertThatThrownBy(
                () ->
                        service.evaluate(
                                request(
                                        "25.00",
                                        "USD",
                                        APPLICABLE_ON)))
                .isInstanceOf(
                        PromotionEvaluationException.class)
                .satisfies(
                        exception ->
                                assertThat(
                                        ((PromotionEvaluationException)
                                                exception)
                                                .failure())
                                        .isEqualTo(
                                                PromotionEvaluationFailure
                                                        .PROMOTION_INACTIVE))
                .hasMessage(
                        "Promotion is inactive.");

        verify(eligibilityStore, never())
                .isPlanEligible(
                        PROMOTION_ID,
                        PLAN_ID);
    }

    @Test
    void rejectsPromotionBeforeValidityPeriod() {
        PromotionDetails promotion =
                percentagePromotion(
                        true,
                        VALID_FROM,
                        VALID_UNTIL,
                        "10.00");

        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(
                        Optional.of(promotion));

        assertThatThrownBy(
                () ->
                        service.evaluate(
                                request(
                                        "25.00",
                                        "USD",
                                        VALID_FROM.minusDays(1))))
                .isInstanceOf(
                        PromotionEvaluationException.class)
                .satisfies(
                        exception ->
                                assertThat(
                                        ((PromotionEvaluationException)
                                                exception)
                                                .failure())
                                        .isEqualTo(
                                                PromotionEvaluationFailure
                                                        .PROMOTION_NOT_YET_VALID))
                .hasMessage(
                        "Promotion is not valid yet.");

        verify(eligibilityStore, never())
                .isPlanEligible(
                        PROMOTION_ID,
                        PLAN_ID);
    }

    @Test
    void rejectsPromotionAfterValidityPeriod() {
        PromotionDetails promotion =
                percentagePromotion(
                        true,
                        VALID_FROM,
                        VALID_UNTIL,
                        "10.00");

        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(
                        Optional.of(promotion));

        assertThatThrownBy(
                () ->
                        service.evaluate(
                                request(
                                        "25.00",
                                        "USD",
                                        VALID_UNTIL.plusDays(1))))
                .isInstanceOf(
                        PromotionEvaluationException.class)
                .satisfies(
                        exception ->
                                assertThat(
                                        ((PromotionEvaluationException)
                                                exception)
                                                .failure())
                                        .isEqualTo(
                                                PromotionEvaluationFailure
                                                        .PROMOTION_EXPIRED))
                .hasMessage(
                        "Promotion has expired.");

        verify(eligibilityStore, never())
                .isPlanEligible(
                        PROMOTION_ID,
                        PLAN_ID);
    }

    @Test
    void rejectsPromotionForIneligiblePlan() {
        PromotionDetails promotion =
                percentagePromotion(
                        true,
                        VALID_FROM,
                        VALID_UNTIL,
                        "10.00");

        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(
                        Optional.of(promotion));

        when(eligibilityStore.isPlanEligible(
                PROMOTION_ID,
                PLAN_ID))
                .thenReturn(false);

        assertThatThrownBy(
                () ->
                        service.evaluate(
                                request(
                                        "25.00",
                                        "USD",
                                        APPLICABLE_ON)))
                .isInstanceOf(
                        PromotionEvaluationException.class)
                .satisfies(
                        exception ->
                                assertThat(
                                        ((PromotionEvaluationException)
                                                exception)
                                                .failure())
                                        .isEqualTo(
                                                PromotionEvaluationFailure
                                                        .PLAN_NOT_ELIGIBLE))
                .hasMessage(
                        "Promotion is not eligible for membership plan "
                                + PLAN_ID
                                + ".");
    }

    @Test
    void rejectsFixedAmountPromotionWithDifferentCurrency() {
        PromotionDetails promotion =
                fixedAmountPromotion(
                        true,
                        VALID_FROM,
                        VALID_UNTIL,
                        "5.00",
                        "USD");

        prepareEligiblePromotion(
                promotion);

        assertThatThrownBy(
                () ->
                        service.evaluate(
                                request(
                                        "25.00",
                                        "EUR",
                                        APPLICABLE_ON)))
                .isInstanceOf(
                        PromotionEvaluationException.class)
                .satisfies(
                        exception ->
                                assertThat(
                                        ((PromotionEvaluationException)
                                                exception)
                                                .failure())
                                        .isEqualTo(
                                                PromotionEvaluationFailure
                                                        .CURRENCY_MISMATCH))
                .hasMessage(
                        "Fixed amount promotion currency USD "
                                + "does not match membership plan currency EUR.");
    }

    @Test
    void percentagePromotionUsesMembershipCurrency() {
        PromotionDetails promotion =
                percentagePromotion(
                        true,
                        VALID_FROM,
                        VALID_UNTIL,
                        "10.00");

        prepareEligiblePromotion(
                promotion);

        PromotionEvaluationResult result =
                service.evaluate(
                        request(
                                "25.00",
                                "eur",
                                APPLICABLE_ON));

        assertThat(result.promotionCurrency())
                .isNull();

        assertThat(result.currency())
                .isEqualTo("EUR");

        assertThat(result.discountAmount())
                .isEqualByComparingTo("2.50");

        assertThat(result.finalPrice())
                .isEqualByComparingTo("22.50");
    }

    @Test
    void evaluatesZeroPriceWithoutProducingNegativeFinalPrice() {
        PromotionDetails promotion =
                fixedAmountPromotion(
                        true,
                        VALID_FROM,
                        VALID_UNTIL,
                        "5.00",
                        "USD");

        prepareEligiblePromotion(
                promotion);

        PromotionEvaluationResult result =
                service.evaluate(
                        request(
                                "0.00",
                                "USD",
                                APPLICABLE_ON));

        assertThat(result.listPrice())
                .isEqualByComparingTo("0.00");

        assertThat(result.discountAmount())
                .isEqualByComparingTo("0.00");

        assertThat(result.finalPrice())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsMissingEvaluationRequest() {
        assertThatThrownBy(
                () -> service.evaluate(null))
                .isInstanceOf(
                        IllegalArgumentException.class)
                .hasMessage(
                        "Promotion evaluation request "
                                + "must be provided.");

        verify(promotionStore, never())
                .findById(
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requestRejectsMissingPromotionIdentifier() {
        assertThatThrownBy(
                () ->
                        new PromotionEvaluationRequest(
                                null,
                                PLAN_ID,
                                new BigDecimal("25.00"),
                                "USD",
                                APPLICABLE_ON))
                .isInstanceOf(
                        IllegalArgumentException.class)
                .hasMessage(
                        "Promotion identifier must be provided.");
    }

    @Test
    void requestRejectsMissingPlanIdentifier() {
        assertThatThrownBy(
                () ->
                        new PromotionEvaluationRequest(
                                PROMOTION_ID,
                                null,
                                new BigDecimal("25.00"),
                                "USD",
                                APPLICABLE_ON))
                .isInstanceOf(
                        IllegalArgumentException.class)
                .hasMessage(
                        "Membership plan identifier "
                                + "must be provided.");
    }

    @Test
    void requestRejectsMissingPrice() {
        assertThatThrownBy(
                () ->
                        new PromotionEvaluationRequest(
                                PROMOTION_ID,
                                PLAN_ID,
                                null,
                                "USD",
                                APPLICABLE_ON))
                .isInstanceOf(
                        PromotionEvaluationException.class)
                .satisfies(
                        exception ->
                                assertThat(
                                        ((PromotionEvaluationException)
                                                exception)
                                                .failure())
                                        .isEqualTo(
                                                PromotionEvaluationFailure
                                                        .INVALID_PRICE))
                .hasMessage(
                        "Membership plan price must be provided.");
    }

    @Test
    void requestRejectsNegativePrice() {
        assertThatThrownBy(
                () ->
                        new PromotionEvaluationRequest(
                                PROMOTION_ID,
                                PLAN_ID,
                                new BigDecimal("-0.01"),
                                "USD",
                                APPLICABLE_ON))
                .isInstanceOf(
                        PromotionEvaluationException.class)
                .satisfies(
                        exception ->
                                assertThat(
                                        ((PromotionEvaluationException)
                                                exception)
                                                .failure())
                                        .isEqualTo(
                                                PromotionEvaluationFailure
                                                        .INVALID_PRICE))
                .hasMessage(
                        "Membership plan price must not be negative.");
    }

    @Test
    void requestNormalizesCurrency() {
        PromotionEvaluationRequest request =
                new PromotionEvaluationRequest(
                        PROMOTION_ID,
                        PLAN_ID,
                        new BigDecimal("25.00"),
                        " usd ",
                        APPLICABLE_ON);

        assertThat(request.currency())
                .isEqualTo("USD");
    }

    @Test
    void requestRejectsMissingCurrency() {
        assertThatThrownBy(
                () ->
                        new PromotionEvaluationRequest(
                                PROMOTION_ID,
                                PLAN_ID,
                                new BigDecimal("25.00"),
                                null,
                                APPLICABLE_ON))
                .isInstanceOf(
                        PromotionEvaluationException.class)
                .satisfies(
                        exception ->
                                assertThat(
                                        ((PromotionEvaluationException)
                                                exception)
                                                .failure())
                                        .isEqualTo(
                                                PromotionEvaluationFailure
                                                        .INVALID_CURRENCY))
                .hasMessage(
                        "Membership plan currency must be provided.");
    }

    @Test
    void requestRejectsInvalidCurrency() {
        assertThatThrownBy(
                () ->
                        new PromotionEvaluationRequest(
                                PROMOTION_ID,
                                PLAN_ID,
                                new BigDecimal("25.00"),
                                "US",
                                APPLICABLE_ON))
                .isInstanceOf(
                        PromotionEvaluationException.class)
                .satisfies(
                        exception ->
                                assertThat(
                                        ((PromotionEvaluationException)
                                                exception)
                                                .failure())
                                        .isEqualTo(
                                                PromotionEvaluationFailure
                                                        .INVALID_CURRENCY))
                .hasMessage(
                        "Membership plan currency must be a "
                                + "three-letter ISO code.");
    }

    @Test
    void requestRejectsMissingApplicationDate() {
        assertThatThrownBy(
                () ->
                        new PromotionEvaluationRequest(
                                PROMOTION_ID,
                                PLAN_ID,
                                new BigDecimal("25.00"),
                                "USD",
                                null))
                .isInstanceOf(
                        IllegalArgumentException.class)
                .hasMessage(
                        "Promotion application date "
                                + "must be provided.");
    }

    private void prepareEligiblePromotion(
            PromotionDetails promotion) {

        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(
                        Optional.of(promotion));

        when(eligibilityStore.isPlanEligible(
                PROMOTION_ID,
                PLAN_ID))
                .thenReturn(true);
    }

    private static PromotionEvaluationRequest request(
            String listPrice,
            String currency,
            LocalDate applicableOn) {

        return new PromotionEvaluationRequest(
                PROMOTION_ID,
                PLAN_ID,
                new BigDecimal(listPrice),
                currency,
                applicableOn);
    }

    private static PromotionDetails percentagePromotion(
            boolean active,
            LocalDate validFrom,
            LocalDate validUntil,
            String discountValue) {

        return promotion(
                DiscountType.PERCENTAGE,
                discountValue,
                null,
                active,
                validFrom,
                validUntil);
    }

    private static PromotionDetails fixedAmountPromotion(
            boolean active,
            LocalDate validFrom,
            LocalDate validUntil,
            String discountValue,
            String currency) {

        return promotion(
                DiscountType.FIXED_AMOUNT,
                discountValue,
                currency,
                active,
                validFrom,
                validUntil);
    }

    private static PromotionDetails promotion(
            DiscountType discountType,
            String discountValue,
            String currency,
            boolean active,
            LocalDate validFrom,
            LocalDate validUntil) {

        return new PromotionDetails(
                PROMOTION_ID,
                "PROMO-000001",
                "September Discount",
                "Promotion evaluator test.",
                discountType,
                new BigDecimal(discountValue),
                currency,
                validFrom,
                validUntil,
                active,
                CREATED_AT,
                CREATED_AT,
                0);
    }
}
