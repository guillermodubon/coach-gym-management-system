package io.github.guillermodubon.coachgym.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class MembershipFreezeSecurityApiIntegrationTest
        extends AbstractMembershipFreezeApiIntegrationTest {

    @Test
    void unauthenticatedUserCannotFreezeMembership()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(adminSession);

        long version =
                membershipVersion(membershipId);

        mockMvc.perform(
                        post(
                                "/api/v1/memberships/{id}/freeze",
                                membershipId)
                                .with(csrf())
                                .contentType(
                                        "application/json")
                                .content(
                                        freezeBody(
                                                "2026-09-10",
                                                "2026-09-20",
                                                "Medical leave",
                                                version)))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "AUTHENTICATION_REQUIRED"));

        assertThat(membershipStatus(membershipId))
                .isEqualTo("ACTIVE");

        assertThat(openFreezeCount(membershipId))
                .isZero();
    }

    @Test
    void authenticatedUserCannotFreezeWithoutCsrf()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(session);

        long version =
                membershipVersion(membershipId);

        mockMvc.perform(
                        post(
                                "/api/v1/memberships/{id}/freeze",
                                membershipId)
                                .session(session)
                                .contentType("application/json")
                                .content(
                                        freezeBody(
                                                "2026-09-10",
                                                "2026-09-20",
                                                "Medical leave",
                                                version)))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "CSRF_TOKEN_INVALID"));

        assertThat(membershipStatus(membershipId))
                .isEqualTo("ACTIVE");

        assertThat(openFreezeCount(membershipId))
                .isZero();
    }

    @Test
    void maintenanceUserCannotFreezeMembership()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(adminSession);

        long version =
                membershipVersion(membershipId);

        MockHttpSession maintenanceSession =
                loginAsMaintenance();

        freezeMembership(
                maintenanceSession,
                membershipId,
                "2026-09-10",
                "2026-09-20",
                "Medical leave",
                version)
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACCESS_DENIED"));

        assertThat(membershipStatus(membershipId))
                .isEqualTo("ACTIVE");

        assertThat(openFreezeCount(membershipId))
                .isZero();
    }

    @Test
    void unauthenticatedUserCannotReactivateMembership()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(adminSession);

        long version =
                membershipVersion(membershipId);

        freezeMembership(
                adminSession,
                membershipId,
                "2026-09-10",
                "2026-09-20",
                "Medical leave",
                version)
                .andExpect(status().isOk());

        mockMvc.perform(
                        post(
                                "/api/v1/memberships/{id}/reactivate",
                                membershipId)
                                .with(csrf())
                                .contentType(
                                        "application/json")
                                .content(
                                        reactivationBody(
                                                "2026-09-15",
                                                version + 1)))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "AUTHENTICATION_REQUIRED"));

        assertThat(membershipStatus(membershipId))
                .isEqualTo("FROZEN");

        assertThat(openFreezeCount(membershipId))
                .isEqualTo(1);
    }

    @Test
    void authenticatedUserCannotReactivateWithoutCsrf()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(session);

        long version =
                membershipVersion(membershipId);

        freezeMembership(
                session,
                membershipId,
                "2026-09-10",
                "2026-09-20",
                "Medical leave",
                version)
                .andExpect(status().isOk());

        mockMvc.perform(
                        post(
                                "/api/v1/memberships/{id}/reactivate",
                                membershipId)
                                .session(session)
                                .contentType("application/json")
                                .content(
                                        reactivationBody(
                                                "2026-09-15",
                                                version + 1)))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "CSRF_TOKEN_INVALID"));

        assertThat(membershipStatus(membershipId))
                .isEqualTo("FROZEN");

        assertThat(openFreezeCount(membershipId))
                .isEqualTo(1);
    }

    @Test
    void maintenanceUserCannotReactivateMembership()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(adminSession);

        long version =
                membershipVersion(membershipId);

        freezeMembership(
                adminSession,
                membershipId,
                "2026-09-10",
                "2026-09-20",
                "Medical leave",
                version)
                .andExpect(status().isOk());

        MockHttpSession maintenanceSession =
                loginAsMaintenance();

        reactivateMembership(
                maintenanceSession,
                membershipId,
                "2026-09-15",
                version + 1)
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACCESS_DENIED"));

        assertThat(membershipStatus(membershipId))
                .isEqualTo("FROZEN");

        assertThat(openFreezeCount(membershipId))
                .isEqualTo(1);
    }
}
