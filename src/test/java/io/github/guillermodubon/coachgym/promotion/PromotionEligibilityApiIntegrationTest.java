package io.github.guillermodubon.coachgym.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class PromotionEligibilityApiIntegrationTest
        extends AbstractPromotionApiIntegrationTest {

    @Test
    void adminReplacesAndGetsEligiblePlans()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName(
                                        "Eligible Plans")));

        UUID monthlyPlanId =
                createPlan(
                        session,
                        uniqueName(
                                "Monthly Eligibility Plan"));

        UUID annualPlanId =
                createPlan(
                        session,
                        uniqueName(
                                "Annual Eligibility Plan"));

        mockMvc.perform(
                        get(
                                "/api/v1/promotions/{id}/eligible-plans",
                                promotionId)
                                .session(session))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.promotionId")
                                .value(
                                        promotionId.toString()))
                .andExpect(
                        jsonPath("$.promotionVersion")
                                .value(0))
                .andExpect(
                        jsonPath("$.items")
                                .isArray())
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(0));

        mockMvc.perform(
                        put(
                                "/api/v1/promotions/{id}/eligible-plans",
                                promotionId)
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        replaceBody(
                                                0,
                                                monthlyPlanId,
                                                annualPlanId)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.promotionId")
                                .value(
                                        promotionId.toString()))
                .andExpect(
                        jsonPath("$.promotionVersion")
                                .value(1))
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(2))
                .andExpect(
                        jsonPath(
                                "$.items[?(@.planId == '"
                                        + monthlyPlanId
                                        + "')]")
                                .isNotEmpty())
                .andExpect(
                        jsonPath(
                                "$.items[?(@.planId == '"
                                        + annualPlanId
                                        + "')]")
                                .isNotEmpty());

        assertThat(
                eligibilityCount(
                        promotionId))
                .isEqualTo(2);

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select version
                        from gym.promotions
                        where id = ?
                        """,
                        Long.class,
                        promotionId))
                .isEqualTo(1L);

        mockMvc.perform(
                        get(
                                "/api/v1/promotions/{id}/eligible-plans",
                                promotionId)
                                .session(session))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.promotionVersion")
                                .value(1))
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(2));

        assertThat(
                auditCount(
                        promotionId))
                .isEqualTo(1);

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select summary
                        from gym.audit_entries
                        where resource_id = ?
                          and resource_type = 'PROMOTION'
                          and action_code =
                              'PROMOTION_ELIGIBLE_PLANS_CHANGED'
                        """,
                        String.class,
                        promotionId))
                .isEqualTo(
                        "Promotion eligible plans changed.");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select (metadata ->> 'eligiblePlanCount')::integer
                        from gym.audit_entries
                        where resource_id = ?
                          and resource_type = 'PROMOTION'
                          and action_code =
                              'PROMOTION_ELIGIBLE_PLANS_CHANGED'
                        """,
                        Integer.class,
                        promotionId))
                .isEqualTo(2);
    }

    @Test
    void replacingEligibilityRemovesPreviousAssociations()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName(
                                        "Replace Eligibility")));

        UUID firstPlanId =
                createPlan(
                        session,
                        uniqueName(
                                "First Eligible Plan"));

        UUID secondPlanId =
                createPlan(
                        session,
                        uniqueName(
                                "Second Eligible Plan"));

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                firstPlanId,
                secondPlanId)
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.promotionVersion")
                                .value(1));

        replaceEligiblePlans(
                session,
                promotionId,
                1,
                secondPlanId)
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.promotionVersion")
                                .value(2))
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(1))
                .andExpect(
                        jsonPath("$.items[0].planId")
                                .value(
                                        secondPlanId.toString()));

        assertThat(
                eligibilityCount(
                        promotionId))
                .isEqualTo(1);

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
                        firstPlanId))
                .isZero();

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
                        secondPlanId))
                .isEqualTo(1);

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select version
                        from gym.promotions
                        where id = ?
                        """,
                        Long.class,
                        promotionId))
                .isEqualTo(2L);

        assertThat(
                auditCount(
                        promotionId))
                .isEqualTo(2);
    }

    @Test
    void adminCanRemoveAllEligiblePlans()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName(
                                        "Clear Eligibility")));

        UUID planId =
                createPlan(
                        session,
                        uniqueName(
                                "Temporary Eligible Plan"));

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                planId)
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.promotionVersion")
                                .value(1));

        replaceEligiblePlans(
                session,
                promotionId,
                1)
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.promotionVersion")
                                .value(2))
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(0));

        assertThat(
                eligibilityCount(
                        promotionId))
                .isZero();

        assertThat(
                auditCount(
                        promotionId))
                .isEqualTo(2);
    }

    @Test
    void rejectsUnknownEligiblePlan()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName(
                                        "Unknown Plan Eligibility")));

        UUID unknownPlanId =
                UUID.randomUUID();

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                unknownPlanId)
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "ELIGIBLE_PLAN_NOT_FOUND"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Membership plan "
                                                + unknownPlanId
                                                + " was not found."));

        assertThat(
                eligibilityCount(
                        promotionId))
                .isZero();

        assertThat(
                promotionVersion(
                        promotionId))
                .isZero();

        assertThat(
                auditCount(
                        promotionId))
                .isZero();
    }

    @Test
    void rejectsInactiveEligiblePlan()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName(
                                        "Inactive Plan Eligibility")));

        UUID planId =
                createPlan(
                        session,
                        uniqueName(
                                "Inactive Eligible Plan"));

        mockMvc.perform(
                        post(
                                "/api/v1/plans/{id}/deactivate",
                                planId)
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "version": 0
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.active")
                                .value(false))
                .andExpect(
                        jsonPath("$.version")
                                .value(1));

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                planId)
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "ELIGIBLE_PLAN_INACTIVE"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Membership plan "
                                                + planId
                                                + " is inactive and cannot be assigned "
                                                + "to a promotion."));

        assertThat(
                eligibilityCount(
                        promotionId))
                .isZero();

        assertThat(
                promotionVersion(
                        promotionId))
                .isZero();

        assertThat(
                auditCount(
                        promotionId))
                .isZero();
    }

    @Test
    void rejectsStalePromotionVersion()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName(
                                        "Stale Eligibility")));

        UUID firstPlanId =
                createPlan(
                        session,
                        uniqueName(
                                "Initial Eligibility Plan"));

        UUID secondPlanId =
                createPlan(
                        session,
                        uniqueName(
                                "Stale Eligibility Plan"));

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                firstPlanId)
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.promotionVersion")
                                .value(1));

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                secondPlanId)
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "PROMOTION_VERSION_CONFLICT"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Promotion "
                                                + promotionId
                                                + " was modified by another operation. "
                                                + "Expected version 0 but found 1."));

        assertThat(
                eligibilityCount(
                        promotionId))
                .isEqualTo(1);

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select membership_plan_id
                        from gym.promotion_plan_eligibility
                        where promotion_id = ?
                        """,
                        UUID.class,
                        promotionId))
                .isEqualTo(firstPlanId);

        assertThat(
                auditCount(
                        promotionId))
                .isEqualTo(1);
    }

    @Test
    void receptionistCanGetButCannotReplaceEligiblePlans()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                adminSession,
                                uniqueName(
                                        "Reception Eligibility")));

        UUID planId =
                createPlan(
                        adminSession,
                        uniqueName(
                                "Reception Eligible Plan"));

        replaceEligiblePlans(
                adminSession,
                promotionId,
                0,
                planId)
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.promotionVersion")
                                .value(1));

        MockHttpSession receptionistSession =
                loginAsReceptionist();

        mockMvc.perform(
                        get(
                                "/api/v1/promotions/{id}/eligible-plans",
                                promotionId)
                                .session(receptionistSession))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(1))
                .andExpect(
                        jsonPath("$.items[0].planId")
                                .value(
                                        planId.toString()));

        replaceEligiblePlans(
                receptionistSession,
                promotionId,
                1)
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACCESS_DENIED"));

        assertThat(
                eligibilityCount(
                        promotionId))
                .isEqualTo(1);

        assertThat(
                promotionVersion(
                        promotionId))
                .isEqualTo(1L);

        assertThat(
                auditCount(
                        promotionId))
                .isEqualTo(1);
    }

    @Test
    void adminCannotReplaceEligiblePlansWithoutCsrf()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName(
                                        "Eligibility CSRF")));

        UUID planId =
                createPlan(
                        session,
                        uniqueName(
                                "CSRF Eligible Plan"));

        mockMvc.perform(
                        put(
                                "/api/v1/promotions/{id}/eligible-plans",
                                promotionId)
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        replaceBody(
                                                0,
                                                planId)))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "CSRF_TOKEN_INVALID"));

        assertThat(
                eligibilityCount(
                        promotionId))
                .isZero();

        assertThat(
                promotionVersion(
                        promotionId))
                .isZero();

        assertThat(
                auditCount(
                        promotionId))
                .isZero();
    }

    @Test
    void rejectsStructurallyInvalidEligibilityRequest()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName(
                                        "Invalid Eligibility")));

        mockMvc.perform(
                        put(
                                "/api/v1/promotions/{id}/eligible-plans",
                                promotionId)
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "planIds": null,
                                          "promotionVersion": null
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "VALIDATION_FAILED"))
                .andExpect(
                        jsonPath("$.fieldErrors.planIds")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.fieldErrors.promotionVersion")
                                .exists());

        assertThat(
                eligibilityCount(
                        promotionId))
                .isZero();
    }

    private UUID createPlan(
            MockHttpSession session,
            String name)
            throws Exception {

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/plans")
                                        .with(csrf())
                                        .session(session)
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "name": "%s",
                                                  "description": "Plan created for promotion eligibility.",
                                                  "durationValue": 1,
                                                  "durationUnit": "MONTH",
                                                  "listPrice": 25.00,
                                                  "currency": "USD"
                                                }
                                                """
                                                        .formatted(
                                                                name)))
                        .andExpect(status().isCreated())
                        .andReturn();

        return UUID.fromString(
                JsonPath.read(
                        result.getResponse()
                                .getContentAsString(),
                        "$.id"));
    }

    private org.springframework.test.web.servlet.ResultActions
    replaceEligiblePlans(
            MockHttpSession session,
            UUID promotionId,
            long version,
            UUID... planIds)
            throws Exception {

        return mockMvc.perform(
                put(
                        "/api/v1/promotions/{id}/eligible-plans",
                        promotionId)
                        .with(csrf())
                        .session(session)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(
                                replaceBody(
                                        version,
                                        planIds)));
    }

    private static String replaceBody(
            long version,
            UUID... planIds) {

        String jsonPlanIds =
                java.util.Arrays.stream(planIds)
                        .map(
                                planId ->
                                        "\""
                                                + planId
                                                + "\"")
                        .collect(
                                java.util.stream.Collectors
                                        .joining(","));

        return """
                {
                  "planIds": [%s],
                  "promotionVersion": %d
                }
                """
                .formatted(
                        jsonPlanIds,
                        version);
    }

    private int eligibilityCount(
            UUID promotionId) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.promotion_plan_eligibility
                        where promotion_id = ?
                        """,
                        Integer.class,
                        promotionId);

        return count == null ? 0 : count;
    }

    private long promotionVersion(
            UUID promotionId) {

        Long version =
                jdbcTemplate.queryForObject(
                        """
                        select version
                        from gym.promotions
                        where id = ?
                        """,
                        Long.class,
                        promotionId);

        return version == null ? -1 : version;
    }

    private int auditCount(
            UUID promotionId) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.audit_entries
                        where resource_id = ?
                          and resource_type = 'PROMOTION'
                          and action_code =
                              'PROMOTION_ELIGIBLE_PLANS_CHANGED'
                        """,
                        Integer.class,
                        promotionId);

        return count == null ? 0 : count;
    }

    private static String uniqueName(
            String prefix) {

        return prefix
                + " "
                + UUID.randomUUID();
    }
}
