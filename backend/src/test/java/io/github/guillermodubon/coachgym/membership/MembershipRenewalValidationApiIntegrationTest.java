package io.github.guillermodubon.coachgym.membership;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class MembershipRenewalValidationApiIntegrationTest extends AbstractMembershipRenewalApiIntegrationTest {

    @Test
    void rejectsRenewalWithStaleVersion()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        session,
                        uniqueValue("stale-renewal")
                                + "@example.com");

        UUID planId =
                createPlan(
                        session,
                        uniqueValue("Stale Renewal Plan"),
                        "25.00",
                        "USD");

        UUID membershipId =
                createMembership(
                        session,
                        clientId,
                        planId,
                        null,
                        "2026-09-01");

        renewMembership(
                session,
                membershipId,
                planId,
                null,
                null,
                0)
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.version")
                                .value(1));

        renewMembership(
                session,
                membershipId,
                planId,
                null,
                null,
                0)
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_VERSION_CONFLICT"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Membership "
                                                + membershipId
                                                + " was modified by another operation. "
                                                + "Expected version 0 but found 1."));

        assertThat(
                periodCount(
                        membershipId))
                .isEqualTo(2);

        assertThat(
                renewalAuditCount(
                        membershipId))
                .isEqualTo(1);
    }

    @Test
    void rejectsFrozenMembershipRenewal()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        session,
                        uniqueValue("frozen-renewal")
                                + "@example.com");

        UUID planId =
                createPlan(
                        session,
                        uniqueValue("Frozen Renewal Plan"),
                        "25.00",
                        "USD");

        UUID membershipId =
                createMembership(
                        session,
                        clientId,
                        planId,
                        null,
                        "2026-09-01");

        freezeMembershipDirectly(
                membershipId);

        renewMembership(
                session,
                membershipId,
                planId,
                null,
                null,
                1)
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_NOT_RENEWABLE"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Membership "
                                                + membershipId
                                                + " cannot be renewed while its status is "
                                                + "FROZEN."));

        assertThat(
                periodCount(
                        membershipId))
                .isEqualTo(1);

        assertThat(
                renewalAuditCount(
                        membershipId))
                .isZero();
    }

    @Test
    void rejectsPromotionThatIsNotEligibleForRenewalPlan()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        session,
                        uniqueValue("ineligible-renewal")
                                + "@example.com");

        UUID planId =
                createPlan(
                        session,
                        uniqueValue("Ineligible Renewal Plan"),
                        "25.00",
                        "USD");

        UUID promotionId =
                createRenewalPromotion(
                        session,
                        uniqueValue(
                                "Ineligible Renewal Promotion"));

        UUID membershipId =
                createMembership(
                        session,
                        clientId,
                        planId,
                        null,
                        "2026-09-01");

        renewMembership(
                session,
                membershipId,
                planId,
                promotionId,
                null,
                0)
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_PROMOTION_PLAN_NOT_ELIGIBLE"));

        assertThat(
                periodCount(
                        membershipId))
                .isEqualTo(1);

        assertThat(
                membershipVersion(
                        membershipId))
                .isZero();

        assertThat(
                renewalAuditCount(
                        membershipId))
                .isZero();
    }

    @Test
    void rejectsStructurallyInvalidRenewalRequest()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID unknownMembershipId =
                UUID.randomUUID();

        mockMvc.perform(
                        post(
                                "/api/v1/memberships/{id}/renew",
                                unknownMembershipId)
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "membershipPlanId": null,
                                          "promotionId": null,
                                          "startsOn": null,
                                          "version": null
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_FAILED"))
                .andExpect(
                        jsonPath(
                                "$.fieldErrors.membershipPlanId")
                                .exists())
                .andExpect(
                        jsonPath("$.fieldErrors.version")
                                .exists());
    }
}
