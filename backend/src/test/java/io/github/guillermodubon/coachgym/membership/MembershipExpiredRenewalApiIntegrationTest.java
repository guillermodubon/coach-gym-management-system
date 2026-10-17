package io.github.guillermodubon.coachgym.membership;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class MembershipExpiredRenewalApiIntegrationTest extends AbstractMembershipRenewalApiIntegrationTest {

    @Autowired
    private Clock clock;

    @Test
    void adminRenewsExpiredMembershipAndReactivatesIt()
            throws Exception {

        LocalDate operationalDate = LocalDate.now(clock);
        LocalDate renewalStartsOn = operationalDate.plusDays(1);

        MockHttpSession session =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        session,
                        uniqueValue("expired-renewal")
                                + "@example.com");

        UUID planId =
                createPlan(
                        session,
                        uniqueValue("Expired Renewal Plan"),
                        "30.00",
                        "USD");

        UUID membershipId =
                createMembership(
                        session,
                        clientId,
                        planId,
                        null,
                        operationalDate.minusMonths(3).toString());

        expireMembership(
                membershipId);

        MvcResult result =
                renewMembership(
                        session,
                        membershipId,
                        planId,
                        null,
                        renewalStartsOn.toString(),
                        1)
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.status")
                                        .value("ACTIVE"))
                        .andExpect(
                                jsonPath("$.version")
                                        .value(2))
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
                                        .value(renewalStartsOn.toString()))
                        .andExpect(
                                jsonPath(
                                        "$.currentPeriod.baseEndsOn")
                                        .value(renewalStartsOn.plusMonths(1).toString()))
                        .andReturn();

        UUID renewalPeriodId =
                UUID.fromString(
                        com.jayway.jsonpath.JsonPath.read(
                                result.getResponse()
                                        .getContentAsString(),
                                "$.currentPeriod.id"));

        assertThat(
                membershipStatus(
                        membershipId))
                .isEqualTo("ACTIVE");

        assertThat(
                membershipVersion(
                        membershipId))
                .isEqualTo(2L);

        assertThat(
                periodCount(
                        membershipId))
                .isEqualTo(2);

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.membership_status_history
                        where membership_id = ?
                          and membership_period_id = ?
                          and previous_status = 'EXPIRED'
                          and new_status = 'ACTIVE'
                          and reason =
                              'Membership reactivated by renewal.'
                        """,
                        Integer.class,
                        membershipId,
                        renewalPeriodId))
                .isEqualTo(1);

        assertRenewalAudit(
                membershipId,
                renewalPeriodId,
                2,
                null,
                "EXPIRED",
                "ACTIVE",
                true,
                "30.00",
                "0.00",
                "30.00");
    }
}
