package io.github.guillermodubon.coachgym.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class PromotionEvaluatorIntegrationTest
        extends AbstractPromotionApiIntegrationTest {

    @Autowired
    private PromotionEvaluator promotionEvaluator;

    @Test
    void evaluatesPersistedPercentagePromotionForEligiblePlan()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName("Evaluator Percentage")));

        UUID planId =
                createPlan(
                        session,
                        uniqueName("Evaluator Monthly Plan"),
                        "25.00",
                        "USD");

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                planId);

        PromotionEvaluationResult result =
                promotionEvaluator.evaluate(
                        new PromotionEvaluationRequest(
                                promotionId,
                                planId,
                                new BigDecimal("25.00"),
                                "usd",
                                LocalDate.of(
                                        2026,
                                        9,
                                        15)));

        assertThat(result.promotionId())
                .isEqualTo(promotionId);

        assertThat(result.promotionCode())
                .startsWith("PROMO-");

        assertThat(result.discountType())
                .isEqualTo(DiscountType.PERCENTAGE);

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

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.promotion_plan_eligibility
                        where promotion_id = ?
                          and membership_plan_id = ?
                        """,
                        Integer.class,
                        promotionId,
                        planId))
                .isEqualTo(1);
    }

    @Test
    void evaluatesPromotionOnInclusiveValidityBoundaries()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName("Inclusive Validity")));

        UUID planId =
                createPlan(
                        session,
                        uniqueName("Inclusive Validity Plan"),
                        "19.99",
                        "USD");

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                planId);

        PromotionEvaluationResult firstDayResult =
                promotionEvaluator.evaluate(
                        new PromotionEvaluationRequest(
                                promotionId,
                                planId,
                                new BigDecimal("19.99"),
                                "USD",
                                LocalDate.of(
                                        2026,
                                        9,
                                        1)));

        PromotionEvaluationResult lastDayResult =
                promotionEvaluator.evaluate(
                        new PromotionEvaluationRequest(
                                promotionId,
                                planId,
                                new BigDecimal("19.99"),
                                "USD",
                                LocalDate.of(
                                        2026,
                                        9,
                                        30)));

        assertThat(firstDayResult.discountAmount())
                .isEqualByComparingTo("2.00");

        assertThat(firstDayResult.finalPrice())
                .isEqualByComparingTo("17.99");

        assertThat(lastDayResult.discountAmount())
                .isEqualByComparingTo("2.00");

        assertThat(lastDayResult.finalPrice())
                .isEqualByComparingTo("17.99");
    }

    @Test
    void usesFailClosedEligibilityWhenNoPlanIsAssociated()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName("Fail Closed Eligibility")));

        UUID planId =
                createPlan(
                        session,
                        uniqueName("Unassociated Plan"),
                        "25.00",
                        "USD");

        assertEvaluationFailure(
                () ->
                        promotionEvaluator.evaluate(
                                new PromotionEvaluationRequest(
                                        promotionId,
                                        planId,
                                        new BigDecimal("25.00"),
                                        "USD",
                                        LocalDate.of(
                                                2026,
                                                9,
                                                15))),
                PromotionEvaluationFailure.PLAN_NOT_ELIGIBLE,
                "Promotion is not eligible for membership plan "
                        + planId
                        + ".");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.promotion_plan_eligibility
                        where promotion_id = ?
                        """,
                        Integer.class,
                        promotionId))
                .isZero();
    }

    @Test
    void rejectsAPlanDifferentFromTheAssociatedPlan()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName("Specific Plan Eligibility")));

        UUID eligiblePlanId =
                createPlan(
                        session,
                        uniqueName("Associated Plan"),
                        "25.00",
                        "USD");

        UUID otherPlanId =
                createPlan(
                        session,
                        uniqueName("Other Plan"),
                        "30.00",
                        "USD");

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                eligiblePlanId);

        assertEvaluationFailure(
                () ->
                        promotionEvaluator.evaluate(
                                new PromotionEvaluationRequest(
                                        promotionId,
                                        otherPlanId,
                                        new BigDecimal("30.00"),
                                        "USD",
                                        LocalDate.of(
                                                2026,
                                                9,
                                                15))),
                PromotionEvaluationFailure.PLAN_NOT_ELIGIBLE,
                "Promotion is not eligible for membership plan "
                        + otherPlanId
                        + ".");

        PromotionEvaluationResult eligibleResult =
                promotionEvaluator.evaluate(
                        new PromotionEvaluationRequest(
                                promotionId,
                                eligiblePlanId,
                                new BigDecimal("25.00"),
                                "USD",
                                LocalDate.of(
                                        2026,
                                        9,
                                        15)));

        assertThat(eligibleResult.discountAmount())
                .isEqualByComparingTo("2.50");
    }

    @Test
    void evaluatesPersistedFixedAmountPromotion()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        UUID promotionId =
                createFixedAmountPromotion(
                        session,
                        uniqueName("Evaluator Fixed Amount"),
                        "5.00",
                        "USD");

        UUID planId =
                createPlan(
                        session,
                        uniqueName("Fixed Amount Plan"),
                        "25.00",
                        "USD");

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                planId);

        PromotionEvaluationResult result =
                promotionEvaluator.evaluate(
                        new PromotionEvaluationRequest(
                                promotionId,
                                planId,
                                new BigDecimal("25.00"),
                                "usd",
                                LocalDate.of(
                                        2026,
                                        9,
                                        15)));

        assertThat(result.discountType())
                .isEqualTo(DiscountType.FIXED_AMOUNT);

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
    void capsPersistedFixedAmountDiscountAtListPrice()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        UUID promotionId =
                createFixedAmountPromotion(
                        session,
                        uniqueName("Evaluator Fixed Cap"),
                        "15.00",
                        "USD");

        UUID planId =
                createPlan(
                        session,
                        uniqueName("Fixed Cap Plan"),
                        "10.00",
                        "USD");

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                planId);

        PromotionEvaluationResult result =
                promotionEvaluator.evaluate(
                        new PromotionEvaluationRequest(
                                promotionId,
                                planId,
                                new BigDecimal("10.00"),
                                "USD",
                                LocalDate.of(
                                        2026,
                                        9,
                                        15)));

        assertThat(result.listPrice())
                .isEqualByComparingTo("10.00");

        assertThat(result.discountAmount())
                .isEqualByComparingTo("10.00");

        assertThat(result.finalPrice())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsFixedAmountPromotionWithDifferentCurrency()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        UUID promotionId =
                createFixedAmountPromotion(
                        session,
                        uniqueName("Evaluator Currency"),
                        "5.00",
                        "USD");

        UUID planId =
                createPlan(
                        session,
                        uniqueName("Currency Plan"),
                        "25.00",
                        "EUR");

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                planId);

        assertEvaluationFailure(
                () ->
                        promotionEvaluator.evaluate(
                                new PromotionEvaluationRequest(
                                        promotionId,
                                        planId,
                                        new BigDecimal("25.00"),
                                        "EUR",
                                        LocalDate.of(
                                                2026,
                                                9,
                                                15))),
                PromotionEvaluationFailure.CURRENCY_MISMATCH,
                "Fixed amount promotion currency USD "
                        + "does not match membership plan currency EUR.");
    }

    @Test
    void rejectsInactivePersistedPromotion()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName("Evaluator Inactive")));

        UUID planId =
                createPlan(
                        session,
                        uniqueName("Inactive Promotion Plan"),
                        "25.00",
                        "USD");

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                planId);

        mockMvc.perform(
                        post(
                                "/api/v1/promotions/{id}/deactivate",
                                promotionId)
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "version": 1
                                        }
                                        """))
                .andExpect(status().isOk());

        assertEvaluationFailure(
                () ->
                        promotionEvaluator.evaluate(
                                new PromotionEvaluationRequest(
                                        promotionId,
                                        planId,
                                        new BigDecimal("25.00"),
                                        "USD",
                                        LocalDate.of(
                                                2026,
                                                9,
                                                15))),
                PromotionEvaluationFailure.PROMOTION_INACTIVE,
                "Promotion is inactive.");
    }

    @Test
    void rejectsPersistedPromotionBeforeValidityPeriod()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName("Evaluator Future")));

        UUID planId =
                createPlan(
                        session,
                        uniqueName("Future Promotion Plan"),
                        "25.00",
                        "USD");

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                planId);

        assertEvaluationFailure(
                () ->
                        promotionEvaluator.evaluate(
                                new PromotionEvaluationRequest(
                                        promotionId,
                                        planId,
                                        new BigDecimal("25.00"),
                                        "USD",
                                        LocalDate.of(
                                                2026,
                                                8,
                                                31))),
                PromotionEvaluationFailure.PROMOTION_NOT_YET_VALID,
                "Promotion is not valid yet.");
    }

    @Test
    void rejectsPersistedPromotionAfterValidityPeriod()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName("Evaluator Expired")));

        UUID planId =
                createPlan(
                        session,
                        uniqueName("Expired Promotion Plan"),
                        "25.00",
                        "USD");

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                planId);

        assertEvaluationFailure(
                () ->
                        promotionEvaluator.evaluate(
                                new PromotionEvaluationRequest(
                                        promotionId,
                                        planId,
                                        new BigDecimal("25.00"),
                                        "USD",
                                        LocalDate.of(
                                                2026,
                                                10,
                                                1))),
                PromotionEvaluationFailure.PROMOTION_EXPIRED,
                "Promotion has expired.");
    }

    @Test
    void rejectsUnknownPersistedPromotion()
            throws Exception {

        UUID unknownPromotionId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        assertEvaluationFailure(
                () ->
                        promotionEvaluator.evaluate(
                                new PromotionEvaluationRequest(
                                        unknownPromotionId,
                                        planId,
                                        new BigDecimal("25.00"),
                                        "USD",
                                        LocalDate.of(
                                                2026,
                                                9,
                                                15))),
                PromotionEvaluationFailure.PROMOTION_NOT_FOUND,
                "Promotion "
                        + unknownPromotionId
                        + " was not found.");
    }

    private UUID createPlan(
            MockHttpSession session,
            String name,
            String listPrice,
            String currency)
            throws Exception {

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/plans")
                                        .with(csrf())
                                        .session(session)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "name": "%s",
                                                  "description": "Plan created for promotion evaluation.",
                                                  "durationValue": 1,
                                                  "durationUnit": "MONTH",
                                                  "listPrice": %s,
                                                  "currency": "%s"
                                                }
                                                """
                                                        .formatted(
                                                                name,
                                                                listPrice,
                                                                currency)))
                        .andExpect(status().isCreated())
                        .andReturn();

        return UUID.fromString(
                JsonPath.read(
                        result.getResponse().getContentAsString(),
                        "$.id"));
    }

    private UUID createFixedAmountPromotion(
            MockHttpSession session,
            String name,
            String discountValue,
            String currency)
            throws Exception {

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/promotions")
                                        .with(csrf())
                                        .session(session)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "name": "%s",
                                                  "description": "Fixed promotion created for evaluation.",
                                                  "discountType": "FIXED_AMOUNT",
                                                  "discountValue": %s,
                                                  "currency": "%s",
                                                  "validFrom": "2026-09-01",
                                                  "validUntil": "2026-09-30"
                                                }
                                                """
                                                        .formatted(
                                                                name,
                                                                discountValue,
                                                                currency)))
                        .andExpect(status().isCreated())
                        .andReturn();

        return promotionId(result);
    }

    private void replaceEligiblePlans(
            MockHttpSession session,
            UUID promotionId,
            long promotionVersion,
            UUID... planIds)
            throws Exception {

        mockMvc.perform(
                        put(
                                "/api/v1/promotions/{id}/eligible-plans",
                                promotionId)
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        replaceEligiblePlansBody(
                                                promotionVersion,
                                                planIds)))
                .andExpect(status().isOk());
    }

    private static String replaceEligiblePlansBody(
            long promotionVersion,
            UUID... planIds) {

        String jsonPlanIds =
                Arrays.stream(planIds)
                        .map(planId -> "\"" + planId + "\"")
                        .collect(Collectors.joining(","));

        return """
                {
                  "planIds": [%s],
                  "promotionVersion": %d
                }
                """
                .formatted(
                        jsonPlanIds,
                        promotionVersion);
    }

    private static void assertEvaluationFailure(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable operation,
            PromotionEvaluationFailure expectedFailure,
            String expectedMessage) {

        assertThatThrownBy(operation)
                .isInstanceOf(PromotionEvaluationException.class)
                .satisfies(
                        exception ->
                                assertThat(
                                        ((PromotionEvaluationException)
                                                exception)
                                                .failure())
                                        .isEqualTo(expectedFailure))
                .hasMessage(expectedMessage);
    }

    private static String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID();
    }
}
