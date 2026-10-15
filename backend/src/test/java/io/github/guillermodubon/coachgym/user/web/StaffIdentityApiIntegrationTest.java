package io.github.guillermodubon.coachgym.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class StaffIdentityApiIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final UUID INITIAL_BRANCH_ID =
            UUID.fromString("7b0bf7d5-5184-43d2-8f9a-200000000002");

    @Test
    void organizationAdminCanCreateAndSearchAnInvitationWithoutExposingRecipientOrToken()
            throws Exception {
        MockHttpSession session = loginAsAdmin();
        String invitedEmail = "invitee-" + UUID.randomUUID() + "@example.test";
        MvcResult created = mockMvc.perform(post("/api/v1/staff-invitations")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitationRequest(invitedEmail)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.maskedEmail").value("i***@example.test"))
                .andExpect(jsonPath("$.proposedRole").value("RECEPTIONIST"))
                .andExpect(jsonPath("$.proposedScope").value("BRANCH"))
                .andReturn();

        assertThat(created.getResponse().getContentAsString())
                .doesNotContain(invitedEmail, "token", "passwordHash");
        UUID invitationId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.invitationId"));
        String auditMetadata = jdbcTemplate.queryForObject(
                "select metadata::text from gym.audit_entries where resource_id = ? "
                        + "and action_code = 'STAFF_INVITATION_CREATED'",
                String.class,
                invitationId);
        assertThat(auditMetadata)
                .contains("i***@example.test", "RECEPTIONIST", "BRANCH")
                .doesNotContain(invitedEmail, "token", "password");

        mockMvc.perform(get("/api/v1/staff-invitations")
                        .session(session)
                        .param("status", "PENDING")
                        .param("role", "RECEPTIONIST")
                        .param("scope", "BRANCH")
                        .param("branchId", INITIAL_BRANCH_ID.toString())
                        .param("email", invitedEmail.substring(0, 8))
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "EXPIRES_AT")
                        .param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].invitationId").value(invitationId.toString()))
                .andExpect(jsonPath("$.items[0].maskedEmail").value("i***@example.test"));
    }

    @Test
    void publicInvitationInspectionUsesOneGenericUnavailableResponseAndSafeHeaders()
            throws Exception {
        mockMvc.perform(post("/api/v1/staff-invitations/inspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + "A".repeat(43) + "\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/staff-invitations/inspect")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + "A".repeat(43) + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.detail").value("Invitation is not available."));
    }

    @Test
    void passwordRecoveryAcknowledgementIsIdenticalForKnownAndUnknownAddresses()
            throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-recovery-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.test\"}"))
                .andExpect(status().isForbidden());

        String known = mockMvc.perform(post("/api/v1/auth/password-recovery-requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"incident-admin@example.test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(post("/api/v1/auth/password-recovery-requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-" + UUID.randomUUID() + "@example.test\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(known).isEqualTo(unknown);
        assertThat(known).doesNotContain("incident-admin", "token");
    }

    @Test
    void identityAdministrationRejectsAnonymousAndReceptionistActors() throws Exception {
        mockMvc.perform(get("/api/v1/staff-invitations"))
                .andExpect(status().isUnauthorized());

        MockHttpSession receptionist = loginAsReceptionist();
        mockMvc.perform(get("/api/v1/staff-invitations").session(receptionist))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizationAdminMustReauthenticateBeforeInvitingAnotherAdministrator() throws Exception {
        MockHttpSession session = loginAsAdmin();
        String email = "new-admin-" + UUID.randomUUID() + "@example.test";

        mockMvc.perform(post("/api/v1/staff-invitations")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminInvitationRequest(email, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAFF_IDENTITY_OPERATION_FORBIDDEN"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.staff_invitations where email_normalized=lower(?)",
                Integer.class,
                email)).isZero();

        mockMvc.perform(post("/api/v1/staff-invitations")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminInvitationRequest(email, ADMIN_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.proposedRole").value("ADMIN"))
                .andExpect(jsonPath("$.proposedScope").value("ORGANIZATION"));
    }

    @Test
    void identityLifecycleEndpointsRequireCsrfRejectSelfOperationsAndSupportTransitions()
            throws Exception {
        MockHttpSession session = loginAsAdmin();
        String selfRequest = identityStatusRequest(
                securityVersion(adminId), "self-suspension", null);

        mockMvc.perform(post("/api/v1/staff/{userId}/suspend", adminId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfRequest))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/staff/{userId}/suspend", adminId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfRequest))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAFF_IDENTITY_OPERATION_FORBIDDEN"));

        String username = "identity-lifecycle-" + UUID.randomUUID();
        UUID targetId = provisionUser(
                username, username + "@example.test", "Lifecycle-test-password-9!", "RECEPTIONIST");
        long version = securityVersion(targetId);

        mockMvc.perform(post("/api/v1/staff/{userId}/suspend", targetId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identityStatusRequest(version, "temporary leave", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.staff_branch_assignments where user_id=? and status='ACTIVE'",
                Integer.class,
                targetId)).isEqualTo(1);

        version = securityVersion(targetId);
        mockMvc.perform(post("/api/v1/staff/{userId}/reactivate", targetId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identityStatusRequest(version, "return to work", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        version = securityVersion(targetId);
        mockMvc.perform(post("/api/v1/staff/{userId}/deactivate", targetId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identityStatusRequest(version, "employment ended", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.staff_branch_assignments where user_id=? and status='ACTIVE'",
                Integer.class,
                targetId)).isZero();
    }

    @Test
    void roleScopeEndpointRequiresCsrfAndCurrentPasswordReauthentication() throws Exception {
        MockHttpSession session = loginAsAdmin();
        String username = "identity-promotion-" + UUID.randomUUID();
        UUID targetId = provisionUser(
                username, username + "@example.test", "Promotion-test-password-9!", "RECEPTIONIST");
        String request = """
                {
                  "roles": ["ADMIN"],
                  "scope": "BRANCH",
                  "reason": "Approved branch administrator promotion",
                  "expectedVersion": %d,
                  "currentPassword": "%s"
                }
                """.formatted(securityVersion(targetId), ADMIN_PASSWORD);

        mockMvc.perform(put("/api/v1/staff/{userId}/role-scope", targetId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/staff/{userId}/role-scope", targetId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.scope").value("BRANCH"));
    }

    @Test
    void branchAdministratorCannotManageOrganizationInvitations() throws Exception {
        String username = "identity-branch-admin-" + UUID.randomUUID();
        String password = "Branch-admin-password-9!";
        UUID branchAdminId = provisionUser(
                username, username + "@example.test", password, "ADMIN");
        jdbcTemplate.update(
                "update gym.staff_scopes set scope_type='BRANCH', version=version+1 where user_id=?",
                branchAdminId);
        MockHttpSession session = loginWithBranchContext(username, password);

        mockMvc.perform(get("/api/v1/staff-invitations").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAFF_IDENTITY_OPERATION_FORBIDDEN"));
    }

    @Test
    void passwordRecoveryCompletionUsesGenericUnavailableProblemAndSafeHeaders() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-recovery/complete")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "new-recovery-password-917!",
                                  "passwordConfirmation": "new-recovery-password-917!"
                                }
                                """.formatted("A".repeat(43))))
                .andExpect(status().isConflict())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.code").value("PASSWORD_RECOVERY_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.detail").value("Password recovery request is not available."));
    }

    @Test
    void inviteAcceptanceRejectsFieldsOutsideTheAllowlist() throws Exception {
        mockMvc.perform(post("/api/v1/staff-invitations/accept")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "password": "Valid-strong-password-9!",
                                  "passwordConfirmation": "Valid-strong-password-9!",
                                  "firstName": "Invited",
                                  "lastName": "Staff",
                                  "role": "ADMIN"
                                }
                                """.formatted("A".repeat(43))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private static String invitationRequest(String email) {
        return """
                {
                  "email": "%s",
                  "proposedRole": "RECEPTIONIST",
                  "proposedScope": "BRANCH",
                  "branchIds": ["%s"]
                }
                """.formatted(email, INITIAL_BRANCH_ID);
    }

    private static String adminInvitationRequest(String email, String currentPassword) {
        String passwordField = currentPassword == null
                ? "" : ",\n  \"currentPassword\": \"" + currentPassword + "\"";
        return """
                {
                  "email": "%s",
                  "proposedRole": "ADMIN",
                  "proposedScope": "ORGANIZATION",
                  "branchIds": []%s
                }
                """.formatted(email, passwordField);
    }

    private static String identityStatusRequest(long expectedVersion, String reason, String currentPassword) {
        String passwordField = currentPassword == null
                ? "" : ",\n  \"currentPassword\": \"" + currentPassword + "\"";
        return """
                {
                  "expectedVersion": %d,
                  "reason": "%s"%s
                }
                """.formatted(expectedVersion, reason, passwordField);
    }

    private long securityVersion(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select security_version from gym.users where id=?",
                Long.class,
                userId);
    }

    private MockHttpSession loginWithBranchContext(String username, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(put("/api/v1/me/branch-context")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"%s\"}".formatted(INITIAL_BRANCH_ID)))
                .andExpect(status().isOk());
        return session;
    }
}
