package io.github.guillermodubon.coachgym.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class MembershipCreationApiIntegrationTest
        extends AbstractMembershipApiIntegrationTest {

    @Test
    void adminCreatesMembershipWithoutPromotion()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        session,
                        uniqueValue("membership")
                                + "@example.com");

        UUID planId =
                createPlan(
                        session,
                        uniqueValue("Monthly Access"),
                        "25.00",
                        "USD");

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/memberships")
                                        .with(csrf())
                                        .session(session)
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                membershipBody(
                                                        clientId,
                                                        planId,
                                                        null,
                                                        "2026-09-01")))
                        .andExpect(status().isCreated())
                        .andExpect(
                                header().string(
                                        "Location",
                                        Matchers.matchesPattern(
                                                "http://localhost/api/v1/memberships/"
                                                        + "[0-9a-f-]+")))
                        .andExpect(
                                jsonPath("$.id")
                                        .isNotEmpty())
                        .andExpect(
                                jsonPath("$.membershipCode")
                                        .value(
                                                Matchers.matchesPattern(
                                                        "MEM-[0-9]{6}")))
                        .andExpect(
                                jsonPath("$.clientId")
                                        .value(
                                                clientId.toString()))
                        .andExpect(
                                jsonPath("$.status")
                                        .value("ACTIVE"))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.periodNumber")
                                        .value(1))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.source")
                                        .value("INITIAL"))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.pricing.membershipPlanId")
                                        .value(
                                                planId.toString()))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.pricing.listPrice")
                                        .value(25.00))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.pricing.currency")
                                        .value("USD"))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.pricing.promotion")
                                        .doesNotExist())
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.pricing.discountAmount")
                                        .value(0.00))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.pricing.finalPrice")
                                        .value(25.00))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.startsOn")
                                        .value("2026-09-01"))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.baseEndsOn")
                                        .value("2026-10-01"))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.effectiveEndsOn")
                                        .value("2026-10-01"))
                        .andExpect(
                                jsonPath("$.version")
                                        .value(0))
                        .andReturn();

        UUID membershipId =
                responseId(result);

        assertMembershipRows(
                membershipId,
                clientId,
                planId);

        assertMembershipWithoutPromotion(
                membershipId);

        assertInitialStatusHistory(
                membershipId);

        assertMembershipAudit(
                membershipId,
                null,
                "25.00",
                "0.00",
                "25.00");

        mockMvc.perform(
                        get(
                                "/api/v1/memberships/{id}",
                                membershipId)
                                .session(session))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        membershipId.toString()))
                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE"))
                .andExpect(
                        jsonPath(
                                "$.currentPeriod.pricing.planCode")
                                .value(
                                        Matchers.matchesPattern(
                                                "PLAN-[0-9]{6}")))
                .andExpect(
                        jsonPath(
                                "$.currentPeriod.pricing.finalPrice")
                                .value(25.00));
    }

    @Test
    void adminCreatesMembershipWithPercentagePromotion()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        session,
                        uniqueValue("promoted-member")
                                + "@example.com");

        UUID planId =
                createPlan(
                        session,
                        uniqueValue("Promoted Monthly Plan"),
                        "25.00",
                        "USD");

        UUID promotionId =
                createPercentagePromotion(
                        session,
                        uniqueValue(
                                "Membership Percentage"));

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                planId);

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/memberships")
                                        .with(csrf())
                                        .session(session)
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                membershipBody(
                                                        clientId,
                                                        planId,
                                                        promotionId,
                                                        "2026-09-15")))
                        .andExpect(status().isCreated())
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.pricing.promotion.promotionId")
                                        .value(
                                                promotionId.toString()))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.pricing.promotion.discountType")
                                        .value("PERCENTAGE"))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.pricing.promotion.discountValue")
                                        .value(10.00))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.pricing.promotion.currency")
                                        .doesNotExist())
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.pricing.listPrice")
                                        .value(25.00))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.pricing.discountAmount")
                                        .value(2.50))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.pricing.finalPrice")
                                        .value(22.50))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.baseEndsOn")
                                        .value("2026-10-15"))
                        .andReturn();

        UUID membershipId =
                responseId(result);

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select promotion_id
                        from gym.membership_periods
                        where membership_id = ?
                        """,
                        UUID.class,
                        membershipId))
                .isEqualTo(promotionId);

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select promotion_type_snapshot
                        from gym.membership_periods
                        where membership_id = ?
                        """,
                        String.class,
                        membershipId))
                .isEqualTo("PERCENTAGE");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select promotion_value_snapshot
                        from gym.membership_periods
                        where membership_id = ?
                        """,
                        BigDecimal.class,
                        membershipId))
                .isEqualByComparingTo("10.00");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select discount_amount
                        from gym.membership_periods
                        where membership_id = ?
                        """,
                        BigDecimal.class,
                        membershipId))
                .isEqualByComparingTo("2.50");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select final_price
                        from gym.membership_periods
                        where membership_id = ?
                        """,
                        BigDecimal.class,
                        membershipId))
                .isEqualByComparingTo("22.50");

        assertMembershipAudit(
                membershipId,
                promotionId,
                "25.00",
                "2.50",
                "22.50");
    }

    @Test
    void receptionistCanCreateAndGetMembership()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        adminSession,
                        uniqueValue("reception-member")
                                + "@example.com");

        UUID planId =
                createPlan(
                        adminSession,
                        uniqueValue("Reception Plan"),
                        "30.00",
                        "USD");

        MockHttpSession receptionistSession =
                loginAsReceptionist();

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/memberships")
                                        .with(csrf())
                                        .session(
                                                receptionistSession)
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                membershipBody(
                                                        clientId,
                                                        planId,
                                                        null,
                                                        "2026-09-01")))
                        .andExpect(status().isCreated())
                        .andExpect(
                                jsonPath("$.status")
                                        .value("ACTIVE"))
                        .andReturn();

        UUID membershipId =
                responseId(result);

        mockMvc.perform(
                        get(
                                "/api/v1/memberships/{id}",
                                membershipId)
                                .session(
                                        receptionistSession))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        membershipId.toString()));

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select actor_identifier_snapshot
                        from gym.audit_entries
                        where resource_id = ?
                          and action_code =
                              'MEMBERSHIP_CREATED'
                        """,
                        String.class,
                        membershipId))
                .isEqualTo(RECEPTIONIST_USERNAME);
    }

    @Test
    void rejectsSecondCurrentMembershipForSameClient()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        session,
                        uniqueValue("duplicate-member")
                                + "@example.com");

        UUID planId =
                createPlan(
                        session,
                        uniqueValue("Duplicate Membership Plan"),
                        "25.00",
                        "USD");

        createMembership(
                session,
                clientId,
                planId,
                null);

        mockMvc.perform(
                        post("/api/v1/memberships")
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        membershipBody(
                                                clientId,
                                                planId,
                                                null,
                                                "2026-10-01")))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "CURRENT_MEMBERSHIP_ALREADY_EXISTS"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Client "
                                                + clientId
                                                + " already has an active "
                                                + "or frozen membership."));

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.memberships
                        where client_id = ?
                          and status in (
                              'ACTIVE',
                              'FROZEN'
                          )
                        """,
                        Integer.class,
                        clientId))
                .isEqualTo(1);
    }

    @Test
    void rejectsInactiveClient()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        session,
                        uniqueValue("inactive-member")
                                + "@example.com");

        UUID planId =
                createPlan(
                        session,
                        uniqueValue("Inactive Client Plan"),
                        "25.00",
                        "USD");

        jdbcTemplate.update(
                """
                update gym.clients
                set status = 'INACTIVE',
                    deactivated_at = current_timestamp,
                    deactivation_reason = 'Integration test'
                where id = ?
                """,
                clientId);

        mockMvc.perform(
                        post("/api/v1/memberships")
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        membershipBody(
                                                clientId,
                                                planId,
                                                null,
                                                "2026-09-01")))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_CLIENT_INACTIVE"));

        assertThat(
                membershipCount(clientId))
                .isZero();
    }

    @Test
    void rejectsInactivePlan()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        session,
                        uniqueValue("inactive-plan-member")
                                + "@example.com");

        UUID planId =
                createPlan(
                        session,
                        uniqueValue("Inactive Membership Plan"),
                        "25.00",
                        "USD");

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
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/v1/memberships")
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        membershipBody(
                                                clientId,
                                                planId,
                                                null,
                                                "2026-09-01")))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_PLAN_NOT_AVAILABLE"));

        assertThat(
                membershipCount(clientId))
                .isZero();
    }

    @Test
    void rejectsPromotionNotEligibleForPlan()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        session,
                        uniqueValue("ineligible-promotion-member")
                                + "@example.com");

        UUID planId =
                createPlan(
                        session,
                        uniqueValue("Ineligible Promotion Plan"),
                        "25.00",
                        "USD");

        UUID promotionId =
                createPercentagePromotion(
                        session,
                        uniqueValue(
                                "Unassociated Promotion"));

        mockMvc.perform(
                        post("/api/v1/memberships")
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        membershipBody(
                                                clientId,
                                                planId,
                                                promotionId,
                                                "2026-09-15")))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_PROMOTION_PLAN_NOT_ELIGIBLE"));

        assertThat(
                membershipCount(clientId))
                .isZero();
    }

    @Test
    void rejectsUnknownClientAndMembership()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID unknownClientId =
                UUID.randomUUID();

        UUID planId =
                createPlan(
                        session,
                        uniqueValue("Unknown Client Plan"),
                        "25.00",
                        "USD");

        mockMvc.perform(
                        post("/api/v1/memberships")
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        membershipBody(
                                                unknownClientId,
                                                planId,
                                                null,
                                                "2026-09-01")))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_CLIENT_NOT_FOUND"));

        UUID unknownMembershipId =
                UUID.randomUUID();

        mockMvc.perform(
                        get(
                                "/api/v1/memberships/{id}",
                                unknownMembershipId)
                                .session(session))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_NOT_FOUND"));
    }

    @Test
    void rejectsUnauthenticatedAndMissingCsrfRequests()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        adminSession,
                        uniqueValue("csrf-member")
                                + "@example.com");

        UUID planId =
                createPlan(
                        adminSession,
                        uniqueValue("CSRF Membership Plan"),
                        "25.00",
                        "USD");

        mockMvc.perform(
                        post("/api/v1/memberships")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        membershipBody(
                                                clientId,
                                                planId,
                                                null,
                                                "2026-09-01")))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "AUTHENTICATION_REQUIRED"));

        mockMvc.perform(
                        post("/api/v1/memberships")
                                .session(adminSession)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        membershipBody(
                                                clientId,
                                                planId,
                                                null,
                                                "2026-09-01")))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "CSRF_TOKEN_INVALID"));

        assertThat(
                membershipCount(clientId))
                .isZero();
    }


    @Test
    void rejectsStructurallyInvalidRequest()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        mockMvc.perform(
                        post("/api/v1/memberships")
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "clientId": null,
                                          "membershipPlanId": null,
                                          "promotionId": null,
                                          "startsOn": null
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_FAILED"))
                .andExpect(
                        jsonPath(
                                "$.fieldErrors.clientId")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.fieldErrors.membershipPlanId")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.fieldErrors.startsOn")
                                .exists());
    }

    private UUID createMembership(
            MockHttpSession session,
            UUID clientId,
            UUID planId,
            UUID promotionId)
            throws Exception {

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/memberships")
                                        .with(csrf())
                                        .session(session)
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                membershipBody(
                                                        clientId,
                                                        planId,
                                                        promotionId,
                                                        "2026-09-01")))
                        .andExpect(status().isCreated())
                        .andReturn();

        return responseId(result);
    }

    private void assertMembershipRows(
            UUID membershipId,
            UUID clientId,
            UUID planId) {

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.memberships
                        where id = ?
                          and client_id = ?
                          and status = 'ACTIVE'
                        """,
                        Integer.class,
                        membershipId,
                        clientId))
                .isEqualTo(1);

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.membership_periods
                        where membership_id = ?
                          and membership_plan_id = ?
                          and period_number = 1
                          and period_source = 'INITIAL'
                        """,
                        Integer.class,
                        membershipId,
                        planId))
                .isEqualTo(1);

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.membership_status_history
                        where membership_id = ?
                          and previous_status is null
                          and new_status = 'ACTIVE'
                        """,
                        Integer.class,
                        membershipId))
                .isEqualTo(1);
    }

    private void assertMembershipWithoutPromotion(
            UUID membershipId) {

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.membership_periods
                        where membership_id = ?
                          and promotion_id is null
                          and promotion_code_snapshot is null
                          and promotion_name_snapshot is null
                          and promotion_type_snapshot is null
                          and promotion_value_snapshot is null
                          and promotion_currency_snapshot is null
                          and discount_amount = 0
                          and final_price = list_price
                        """,
                        Integer.class,
                        membershipId))
                .isEqualTo(1);
    }

    private void assertInitialStatusHistory(
            UUID membershipId) {

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select reason
                        from gym.membership_status_history
                        where membership_id = ?
                          and previous_status is null
                          and new_status = 'ACTIVE'
                        """,
                        String.class,
                        membershipId))
                .isEqualTo(
                        "Initial membership creation.");
    }

    private void assertMembershipAudit(
            UUID membershipId,
            UUID promotionId,
            String listPrice,
            String discountAmount,
            String finalPrice) {

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.audit_entries
                        where resource_id = ?
                          and resource_type = 'MEMBERSHIP'
                          and action_code =
                              'MEMBERSHIP_CREATED'
                        """,
                        Integer.class,
                        membershipId))
                .isEqualTo(1);

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select summary
                        from gym.audit_entries
                        where resource_id = ?
                          and action_code =
                              'MEMBERSHIP_CREATED'
                        """,
                        String.class,
                        membershipId))
                .isEqualTo("Membership created.");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select metadata ->> 'listPrice'
                        from gym.audit_entries
                        where resource_id = ?
                          and action_code =
                              'MEMBERSHIP_CREATED'
                        """,
                        String.class,
                        membershipId))
                .isEqualTo(listPrice);

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select metadata ->> 'discountAmount'
                        from gym.audit_entries
                        where resource_id = ?
                          and action_code =
                              'MEMBERSHIP_CREATED'
                        """,
                        String.class,
                        membershipId))
                .isEqualTo(discountAmount);

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select metadata ->> 'finalPrice'
                        from gym.audit_entries
                        where resource_id = ?
                          and action_code =
                              'MEMBERSHIP_CREATED'
                        """,
                        String.class,
                        membershipId))
                .isEqualTo(finalPrice);

        String persistedPromotionId =
                jdbcTemplate.queryForObject(
                        """
                        select metadata ->> 'promotionId'
                        from gym.audit_entries
                        where resource_id = ?
                          and action_code =
                              'MEMBERSHIP_CREATED'
                        """,
                        String.class,
                        membershipId);

        if (promotionId == null) {
            assertThat(persistedPromotionId)
                    .isNull();
        } else {
            assertThat(persistedPromotionId)
                    .isEqualTo(
                            promotionId.toString());
        }
    }

    private int membershipCount(
            UUID clientId) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.memberships
                        where client_id = ?
                        """,
                        Integer.class,
                        clientId);

        return count == null
                ? 0
                : count;
    }
}
