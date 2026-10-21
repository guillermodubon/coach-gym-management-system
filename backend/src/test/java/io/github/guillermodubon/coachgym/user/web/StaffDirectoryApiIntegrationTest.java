package io.github.guillermodubon.coachgym.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class StaffDirectoryApiIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final UUID INITIAL_BRANCH_ID =
            UUID.fromString("7b0bf7d5-5184-43d2-8f9a-200000000002");
    private static final String TEST_PASSWORD = "Directory-test-password-9!";

    @Test
    void directoryRequiresOrganizationAdminAndGetWorksWithoutCsrfOrBranchContext() throws Exception {
        UUID existingStaffId = adminId;
        mockMvc.perform(get("/api/v1/staff"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/staff/{userId}", existingStaffId)
                        .session(loginAsReceptionist()))
                .andExpect(status().isForbidden());

        String branchAdminName = "directory-branch-admin-" + UUID.randomUUID();
        UUID branchAdminId = provisionUser(
                branchAdminName,
                branchAdminName + "@example.test",
                TEST_PASSWORD,
                "ADMIN");
        MockHttpSession branchAdminSession = login(branchAdminName);
        // Keep the session's ADMIN authority but revoke its persisted organization scope.
        jdbcTemplate.update("""
                update gym.staff_scopes
                   set scope_type = 'BRANCH', version = version + 1
                 where user_id = ?
                """, branchAdminId);
        mockMvc.perform(get("/api/v1/staff").session(branchAdminSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/staff/{userId}", UUID.randomUUID())
                        .session(branchAdminSession))
                .andExpect(status().isForbidden());

        MockHttpSession organizationAdminSession = loginWithoutBranchSelection(
                ADMIN_USERNAME, ADMIN_PASSWORD);
        mockMvc.perform(get("/api/v1/staff")
                        .session(organizationAdminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        mockMvc.perform(get("/api/v1/staff/{userId}", existingStaffId)
                        .session(organizationAdminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(existingStaffId.toString()));
        mockMvc.perform(get("/api/v1/staff/{userId}", UUID.randomUUID())
                        .session(organizationAdminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAFF_IDENTITY_NOT_FOUND"));

        // This static path must keep resolving to the assignment controller, not the UUID detail route.
        mockMvc.perform(get("/api/v1/staff/branch-assignments")
                        .session(organizationAdminSession)
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void directorySearchFiltersAndPagesAllPersistedStatusesWithoutEmailLeak() throws Exception {
        MockHttpSession session = loginWithoutBranchSelection(ADMIN_USERNAME, ADMIN_PASSWORD);
        String marker = "cohort" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        List<UUID> ids = new ArrayList<>();
        for (String accountStatus : List.of("ACTIVE", "INACTIVE", "SUSPENDED", "DEACTIVATED")) {
            ids.add(insertDirectoryUser(
                    marker + "-" + accountStatus.toLowerCase(),
                    "Directory", marker + " Shared",
                    marker + "-" + accountStatus.toLowerCase() + "@example.test",
                    accountStatus,
                    "ADMIN",
                    "BRANCH"));
        }
        addActiveAssignment(ids.getFirst());
        addEndedAssignment(ids.getFirst());

        MvcResult firstPage = mockMvc.perform(get("/api/v1/staff")
                        .session(session)
                        .param("search", marker.toUpperCase())
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "LAST_NAME")
                        .param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();
        MvcResult secondPage = mockMvc.perform(get("/api/v1/staff")
                        .session(session)
                        .param("search", marker)
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "LAST_NAME")
                        .param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();

        List<String> pageIds = new ArrayList<>();
        pageIds.addAll(JsonPath.read(firstPage.getResponse().getContentAsString(), "$.items[*].userId"));
        pageIds.addAll(JsonPath.read(secondPage.getResponse().getContentAsString(), "$.items[*].userId"));
        assertThat(pageIds).containsExactlyElementsOf(ids.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .map(UUID::toString)
                .toList());
        assertThat(firstPage.getResponse().getContentAsString())
                .doesNotContain(marker + "-active@example.test")
                .doesNotContain("passwordHash", "lastLoginAt", "assignments");

        mockMvc.perform(get("/api/v1/staff")
                        .session(session)
                        .param("search", marker)
                        .param("status", "INACTIVE")
                        .param("role", "ADMIN")
                        .param("scopeType", "BRANCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].status").value("INACTIVE"));

        mockMvc.perform(get("/api/v1/staff")
                        .session(session)
                        .param("search", marker)
                        .param("page", "99")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(4));

        String noAssignmentToken = "org-no-assignment-" + UUID.randomUUID();
        UUID organizationAdminWithoutAssignment = insertDirectoryUser(
                "directory-" + UUID.randomUUID(),
                "Organization", "Administrator",
                noAssignmentToken + "@example.test",
                "ACTIVE", "ADMIN", "ORGANIZATION");
        mockMvc.perform(get("/api/v1/staff")
                        .session(session)
                        .param("search", noAssignmentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].userId")
                        .value(organizationAdminWithoutAssignment.toString()))
                .andExpect(jsonPath("$.items[0].scope").value("ORGANIZATION"));
        // Keep this fixture from changing later last-organization-admin tests in a shared test database.
        addActiveAssignment(organizationAdminWithoutAssignment);
        jdbcTemplate.update("""
                update gym.staff_scopes
                   set scope_type = 'BRANCH', version = version + 1
                 where user_id = ?
                """, organizationAdminWithoutAssignment);

        mockMvc.perform(get("/api/v1/staff")
                        .session(session)
                        .param("search", marker)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/staff")
                        .session(session)
                        .param("search", marker)
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/staff")
                        .session(session)
                        .param("search", marker)
                        .param("sort", "PASSWORD_HASH"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchTreatsWildcardsLiterallyAndBranchFilterRequiresActiveAssignment() throws Exception {
        MockHttpSession session = loginWithoutBranchSelection(ADMIN_USERNAME, ADMIN_PASSWORD);
        String marker = "literal" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID literalId = insertDirectoryUser(
                "directory-literal-" + marker,
                "Percent%_" + marker,
                "Literal",
                "literal-" + marker + "@example.test",
                "SUSPENDED", "ADMIN", "BRANCH");
        insertDirectoryUser(
                "directory-near-literal-" + marker,
                "PercentXX" + marker,
                "Literal",
                "near-literal-" + marker + "@example.test",
                "SUSPENDED", "ADMIN", "BRANCH");
        String branchFilterToken = "branchfilter" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID branchMatch = insertDirectoryUser(
                "directory-branch-match-" + branchFilterToken,
                "Branch", "Match",
                "branch-match-" + branchFilterToken + "@example.test",
                "ACTIVE", "ADMIN", "BRANCH");
        addActiveAssignment(branchMatch);
        UUID branchNoMatch = insertDirectoryUser(
                "directory-branch-no-match-" + branchFilterToken,
                "Branch", "No Match",
                "branch-no-match-" + branchFilterToken + "@example.test",
                "SUSPENDED", "ADMIN", "BRANCH");
        addEndedAssignment(branchNoMatch);

        mockMvc.perform(get("/api/v1/staff")
                        .session(session)
                        .param("search", "%_" + marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(literalId.toString()));
        mockMvc.perform(get("/api/v1/staff")
                        .session(session)
                        .param("search", branchFilterToken)
                        .param("branchId", INITIAL_BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(branchMatch.toString()));

        String emailSearchToken = "emailonly" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID emailOnlyMatch = insertDirectoryUser(
                "directory-contact-" + UUID.randomUUID(),
                "Contact", "Lookup",
                emailSearchToken + "@example.test",
                "ACTIVE", "ADMIN", "BRANCH");
        addActiveAssignment(emailOnlyMatch);
        MvcResult emailSearch = mockMvc.perform(get("/api/v1/staff")
                        .session(session)
                        .param("search", emailSearchToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(emailOnlyMatch.toString()))
                .andReturn();
        assertThat(emailSearch.getResponse().getContentAsString())
                .doesNotContain(emailSearchToken + "@example.test");
    }

    @Test
    void detailsExposeTheCorrectIndependentVersionsAndSafeFields() throws Exception {
        MockHttpSession session = loginWithoutBranchSelection(ADMIN_USERNAME, ADMIN_PASSWORD);
        String username = "directory-detail-" + UUID.randomUUID();
        UUID userId = insertDirectoryUser(
                username, "Detail", "Projection",
                username + "@example.test", "ACTIVE", "RECEPTIONIST", "BRANCH");
        addActiveAssignment(userId);
        jdbcTemplate.update("update gym.users set version = 9 where id = ?", userId);
        jdbcTemplate.update("update gym.users set security_version = security_version + 1 where id = ?", userId);
        jdbcTemplate.update("update gym.users set security_version = security_version + 1 where id = ?", userId);
        for (int version = 0; version < 3; version++) {
            jdbcTemplate.update("update gym.staff_scopes set version = version + 1 where user_id = ?", userId);
        }

        MvcResult result = mockMvc.perform(get("/api/v1/staff/{userId}", userId)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value(username + "@example.test"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.scopeVersion").value(3))
                .andExpect(jsonPath("$.roles[0]").value("RECEPTIONIST"))
                .andExpect(jsonPath("$.scope").value("BRANCH"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("password", "lastLoginAt", "photo", "assignment", "token");
        assertThat(jdbcTemplate.queryForObject(
                "select version from gym.users where id = ?", Long.class, userId))
                .isEqualTo(9L);
    }

    @Test
    void staleSessionsAndRequiredPasswordChangeRemainBlockedFromDirectoryReads() throws Exception {
        String staleUsername = "directory-stale-session-" + UUID.randomUUID();
        UUID staleUserId = insertDirectoryUser(
                staleUsername, "Stale", "Session",
                staleUsername + "@example.test", "ACTIVE", "ADMIN", "ORGANIZATION");
        addActiveAssignment(staleUserId);
        MockHttpSession staleSession = loginWithoutBranchSelection(staleUsername, TEST_PASSWORD);
        jdbcTemplate.update("""
                update gym.users
                   set security_version = security_version + 1
                 where id = ?
                """, staleUserId);
        jdbcTemplate.update("""
                update gym.staff_scopes
                   set scope_type = 'BRANCH', version = version + 1
                 where user_id = ?
                """, staleUserId);

        mockMvc.perform(get("/api/v1/staff").session(staleSession))
                .andExpect(status().isUnauthorized());
        assertThat(staleSession.isInvalid()).isTrue();

        String bootstrapUsername = "directory-password-required-" + UUID.randomUUID();
        UUID bootstrapUserId = insertDirectoryUser(
                bootstrapUsername, "Bootstrap", "Admin",
                bootstrapUsername + "@example.test", "ACTIVE", "ADMIN", "ORGANIZATION");
        addActiveAssignment(bootstrapUserId);
        jdbcTemplate.update("""
                update gym.users
                   set password_change_required = true,
                       security_version = security_version + 1
                 where id = ?
                """, bootstrapUserId);
        jdbcTemplate.update("""
                update gym.staff_scopes
                   set scope_type = 'BRANCH', version = version + 1
                 where user_id = ?
                """, bootstrapUserId);
        MockHttpSession bootstrapSession = loginWithoutBranchSelection(
                bootstrapUsername, TEST_PASSWORD);

        mockMvc.perform(get("/api/v1/staff").session(bootstrapSession))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession login(String username) throws Exception {
        return loginWithoutBranchSelection(username, TEST_PASSWORD);
    }

    private MockHttpSession loginWithoutBranchSelection(String username, String password)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private UUID insertDirectoryUser(
            String username,
            String firstName,
            String lastName,
            String email,
            String status,
            String role,
            String scope) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.users
                    (id, username, email, password_hash, first_name, last_name, status)
                values (?, ?, ?, ?, ?, ?, ?)
                """, id, username, email, passwordEncoder.encode(TEST_PASSWORD),
                firstName, lastName, status);
        jdbcTemplate.update("""
                insert into gym.user_roles (user_id, role_id)
                select ?, id from gym.roles where role_code = ?
                """, id, role);
        jdbcTemplate.update("""
                insert into gym.staff_scopes (user_id, scope_type, version)
                values (?, ?, 0)
                """, id, scope);
        return id;
    }

    private void addActiveAssignment(UUID userId) {
        jdbcTemplate.update("""
                insert into gym.staff_branch_assignments
                    (id, user_id, branch_id, status, assigned_at, assigned_by_user_id, version)
                values (?, ?, ?, 'ACTIVE', current_timestamp, ?, 0)
                """, UUID.randomUUID(), userId, INITIAL_BRANCH_ID, adminId);
    }

    private void addEndedAssignment(UUID userId) {
        jdbcTemplate.update("""
                insert into gym.staff_branch_assignments
                    (id, user_id, branch_id, status, assigned_at, assigned_by_user_id,
                     ended_at, ended_by_user_id, end_reason, version)
                values (?, ?, ?, 'ENDED', current_timestamp - interval '1 minute', ?,
                        current_timestamp, ?, 'Directory integration fixture', 1)
                """, UUID.randomUUID(), userId, INITIAL_BRANCH_ID, adminId, adminId);
    }
}
