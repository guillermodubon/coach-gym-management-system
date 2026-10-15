package io.github.guillermodubon.coachgym.membership;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class MembershipActiveRenewalApiIntegrationTest extends AbstractMembershipRenewalApiIntegrationTest {

    @Test
    void adminRenewsActiveMembershipFromCurrentPeriodEnd()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        session,
                        uniqueValue("active-renewal")
                                + "@example.com");

        UUID planId =
                createPlan(
                        session,
                        uniqueValue("Active Renewal Plan"),
                        "25.00",
                        "USD");

        UUID membershipId =
                createMembership(
                        session,
                        clientId,
                        planId,
                        null,
                        "2026-09-01");

        MvcResult result =
                renewMembership(
                        session,
                        membershipId,
                        planId,
                        null,
                        "2026-12-01",
                        0)
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.id")
                                        .value(
                                                membershipId.toString()))
                        .andExpect(
                                jsonPath("$.clientId")
                                        .value(
                                                clientId.toString()))
                        .andExpect(
                                jsonPath("$.status")
                                        .value("ACTIVE"))
                        .andExpect(
                                jsonPath("$.version")
                                        .value(1))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.periodNumber")
                                        .value(2))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.source")
                                        .value("RENEWAL"))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.startsOn")
                                        .value("2026-10-01"))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.baseEndsOn")
                                        .value("2026-11-01"))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.effectiveEndsOn")
                                        .value("2026-11-01"))
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
                                        "$.currentPeriod.pricing.discountAmount")
                                        .value(0.00))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.pricing.finalPrice")
                                        .value(25.00))
                        .andReturn();

        UUID renewalPeriodId =
                UUID.fromString(
                        com.jayway.jsonpath.JsonPath.read(
                                result.getResponse()
                                        .getContentAsString(),
                                "$.currentPeriod.id"));

        assertThat(renewalPeriodId)
                .isNotNull();

        assertThat(
                membershipVersion(
                        membershipId))
                .isEqualTo(1L);

        assertThat(
                membershipStatus(
                        membershipId))
                .isEqualTo("ACTIVE");

        assertThat(
                periodCount(
                        membershipId))
                .isEqualTo(2);

        assertPeriod(
                membershipId,
                (short) 1,
                "INITIAL",
                "2026-09-01",
                "2026-10-01");

        assertPeriod(
                membershipId,
                (short) 2,
                "RENEWAL",
                "2026-10-01",
                "2026-11-01");

        assertThat(
                renewalStatusHistoryCount(
                        membershipId))
                .isZero();

        assertRenewalAudit(
                membershipId,
                renewalPeriodId,
                2,
                null,
                "ACTIVE",
                "ACTIVE",
                false,
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
                        jsonPath("$.version")
                                .value(1))
                .andExpect(
                        jsonPath(
                                "$.currentPeriod.periodNumber")
                                .value(2))
                .andExpect(
                        jsonPath(
                                "$.currentPeriod.source")
                                .value("RENEWAL"));
    }

    @Test
    void adminRenewsMembershipWithPercentagePromotion()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        session,
                        uniqueValue("promoted-renewal")
                                + "@example.com");

        UUID planId =
                createPlan(
                        session,
                        uniqueValue("Promoted Renewal Plan"),
                        "25.00",
                        "USD");

        UUID promotionId =
                createRenewalPromotion(
                        session,
                        uniqueValue(
                                "Renewal Percentage"));

        replaceEligiblePlans(
                session,
                promotionId,
                0,
                planId);

        UUID membershipId =
                createMembership(
                        session,
                        clientId,
                        planId,
                        null,
                        "2026-09-01");

        MvcResult result =
                renewMembership(
                        session,
                        membershipId,
                        planId,
                        promotionId,
                        null,
                        0)
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.version")
                                        .value(1))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.periodNumber")
                                        .value(2))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.startsOn")
                                        .value("2026-10-01"))
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
                        .andReturn();

        UUID renewalPeriodId =
                UUID.fromString(
                        com.jayway.jsonpath.JsonPath.read(
                                result.getResponse()
                                        .getContentAsString(),
                                "$.currentPeriod.id"));

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select promotion_id
                        from gym.membership_periods
                        where id = ?
                        """,
                        UUID.class,
                        renewalPeriodId))
                .isEqualTo(promotionId);

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select promotion_type_snapshot
                        from gym.membership_periods
                        where id = ?
                        """,
                        String.class,
                        renewalPeriodId))
                .isEqualTo("PERCENTAGE");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select discount_amount
                        from gym.membership_periods
                        where id = ?
                        """,
                        BigDecimal.class,
                        renewalPeriodId))
                .isEqualByComparingTo("2.50");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select final_price
                        from gym.membership_periods
                        where id = ?
                        """,
                        BigDecimal.class,
                        renewalPeriodId))
                .isEqualByComparingTo("22.50");

        assertRenewalAudit(
                membershipId,
                renewalPeriodId,
                2,
                promotionId,
                "ACTIVE",
                "ACTIVE",
                false,
                "25.00",
                "2.50",
                "22.50");
    }

    @Test
    void receptionistCanRenewMembership()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        adminSession,
                        uniqueValue("reception-renewal")
                                + "@example.com");

        UUID planId =
                createPlan(
                        adminSession,
                        uniqueValue("Reception Renewal Plan"),
                        "25.00",
                        "USD");

        UUID membershipId =
                createMembership(
                        adminSession,
                        clientId,
                        planId,
                        null,
                        "2026-09-01");

        MockHttpSession receptionistSession =
                loginAsReceptionist();

        renewMembership(
                receptionistSession,
                membershipId,
                planId,
                null,
                null,
                0)
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.version")
                                .value(1))
                .andExpect(
                        jsonPath(
                                "$.currentPeriod.periodNumber")
                                .value(2));

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select actor_identifier_snapshot
                        from gym.audit_entries
                        where resource_id = ?
                          and action_code =
                              'MEMBERSHIP_RENEWED'
                        """,
                        String.class,
                        membershipId))
                .isEqualTo(RECEPTIONIST_USERNAME);
    }
}
