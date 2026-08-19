package io.github.guillermodubon.coachgym.membership;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class MembershipRenewalSecurityApiIntegrationTest extends AbstractMembershipRenewalApiIntegrationTest {

    @Test
    void maintenanceUserCannotRenewMembership()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        adminSession,
                        uniqueValue("restricted-renewal")
                                + "@example.com");

        UUID planId =
                createPlan(
                        adminSession,
                        uniqueValue("Restricted Renewal Plan"),
                        "25.00",
                        "USD");

        UUID membershipId =
                createMembership(
                        adminSession,
                        clientId,
                        planId,
                        null,
                        "2026-09-01");

        MockHttpSession maintenanceSession =
                loginAsMaintenance();

        renewMembership(
                maintenanceSession,
                membershipId,
                planId,
                null,
                null,
                0)
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACCESS_DENIED"));

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
    void authenticatedUserCannotRenewWithoutCsrf()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID clientId =
                createClient(
                        session,
                        uniqueValue("csrf-renewal")
                                + "@example.com");

        UUID planId =
                createPlan(
                        session,
                        uniqueValue("CSRF Renewal Plan"),
                        "25.00",
                        "USD");

        UUID membershipId =
                createMembership(
                        session,
                        clientId,
                        planId,
                        null,
                        "2026-09-01");

        mockMvc.perform(
                        post(
                                "/api/v1/memberships/{id}/renew",
                                membershipId)
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        renewalBody(
                                                planId,
                                                null,
                                                null,
                                                0)))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "CSRF_TOKEN_INVALID"));

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
}
