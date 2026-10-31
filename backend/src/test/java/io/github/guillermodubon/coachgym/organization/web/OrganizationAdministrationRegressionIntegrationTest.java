package io.github.guillermodubon.coachgym.organization.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/** Real-session regression coverage for organization reads, branch context and audit. */
class OrganizationAdministrationRegressionIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000001");
    private static final UUID INITIAL_BRANCH_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000002");
    private static final String BRANCH_ADMIN_USERNAME = "organization-regression-branch-admin";
    private static final String BRANCH_ADMIN_PASSWORD = "Regression-branch-admin-password";
    private static final String BRANCH_ADMIN_EMAIL = "organization-regression-admin@example.test";
    private static final String ACCESS_DENIED_DETAIL = "You are not allowed to perform this operation.";

    @Autowired
    private StaffScopeQuery staffScopeQuery;

    private MockHttpSession organizationAdminSession;
    private MockHttpSession branchAdminSession;
    private MockHttpSession receptionistSession;
    private UUID activeUnassignedBranchId;
    private UUID inactiveUnassignedBranchId;

    @BeforeEach
    void prepareOrganizationAndBranchFixtures() throws Exception {
        jdbcTemplate.update(
                "delete from gym.audit_entries where resource_type in ('ORGANIZATION', 'GYM_BRANCH')");

        UUID branchAdminId = provisionUser(
                BRANCH_ADMIN_USERNAME,
                BRANCH_ADMIN_EMAIL,
                BRANCH_ADMIN_PASSWORD,
                "ADMIN");
        jdbcTemplate.update("""
                update gym.staff_scopes
                set scope_type = 'BRANCH', version = version + 1
                where user_id = ? and scope_type = 'ORGANIZATION'
                """, branchAdminId);

        organizationAdminSession = loginAsAdmin();
        receptionistSession = loginAsReceptionist();
        branchAdminSession = login(BRANCH_ADMIN_USERNAME, BRANCH_ADMIN_PASSWORD);
        selectBranch(branchAdminSession, INITIAL_BRANCH_ID)
                .andExpect(status().isOk());

        activeUnassignedBranchId = createBranch(organizationAdminSession, uniqueCode("ACTIVE"));
        inactiveUnassignedBranchId = createBranch(organizationAdminSession, uniqueCode("INACTIVE"));
        mockMvc.perform(post("/api/v1/branches/{id}/deactivate", inactiveUnassignedBranchId)
                        .session(organizationAdminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("Regression fixture state", branchVersion(inactiveUnassignedBranchId))))
                .andExpect(status().isOk());
    }

    @Test
    void organizationAdminWithoutAssignmentsOrSelectionCanWriteAndCreatesPrivateAudit() throws Exception {
        OrganizationAdminFixture unassignedAdmin = provisionOrganizationAdminWithoutAssignments();
        StaffAuthorizationContext persisted = staffScopeQuery.findAuthorizationContext(unassignedAdmin.id())
                .orElseThrow();
        assertThat(persisted.organizationAdmin()).isTrue();
        assertThat(persisted.assignedBranchIds()).isEmpty();

        MockHttpSession session = login(unassignedAdmin.username(), unassignedAdmin.password());
        assertThat(hasSelectedBranch(session)).isFalse();

        String code = uniqueCode("UNASSIGNED");
        MvcResult created = mockMvc.perform(post("/api/v1/branches")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBranchBodyWithPrivateContact(code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.timezone").value("America/El_Salvador"))
                .andReturn();
        UUID branchId = responseId(created);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.staff_branch_assignments where user_id = ?
                """, Integer.class, unassignedAdmin.id())).isZero();

        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                select actor_user_id, actor_identifier_snapshot, action_code,
                       resource_type, resource_id, metadata::text as metadata
                from gym.audit_entries
                where resource_type = 'GYM_BRANCH' and resource_id = ?
                """, branchId);
        assertThat(audit.get("actor_user_id")).isEqualTo(unassignedAdmin.id());
        assertThat(audit.get("actor_identifier_snapshot")).isEqualTo(unassignedAdmin.username());
        assertThat(audit.get("action_code")).isEqualTo("GYM_BRANCH_CREATED");
        assertThat(audit.get("resource_type")).isEqualTo("GYM_BRANCH");
        assertThat(audit.get("resource_id")).isEqualTo(branchId);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = JsonPath.parse((String) audit.get("metadata")).read("$");
        assertThat(metadata).containsOnlyKeys("organizationId")
                .containsEntry("organizationId", ORGANIZATION_ID.toString());
        assertThat((String) audit.get("metadata"))
                .doesNotContain("Private Address Marker", "audit-private@example.test", "+50322223333",
                        "assignment", "scope", "password", "reason");
        assertThat(hasSelectedBranch(session)).isFalse();
    }

    @Test
    void persistedScopeRevocationDeniesTheNextWriteWithoutChangingBusinessState() throws Exception {
        OrganizationAdminFixture secondOrganizationAdmin = provisionOrganizationAdminWithoutAssignments();
        assertThat(staffScopeQuery.findAuthorizationContext(secondOrganizationAdmin.id()))
                .get().extracting(StaffAuthorizationContext::organizationAdmin).isEqualTo(true);

        String attemptedCode = uniqueCode("REVOKED");
        BusinessSnapshot before = businessSnapshot();
        try {
            int updated = jdbcTemplate.update("""
                    update gym.staff_scopes
                    set scope_type = 'BRANCH', version = version + 1
                    where user_id = ? and scope_type = 'ORGANIZATION'
                    """, adminId);
            assertThat(updated).isEqualTo(1);

            StaffAuthorizationContext revoked = staffScopeQuery.findAuthorizationContext(adminId)
                    .orElseThrow();
            assertThat(revoked.roles()).containsExactly(RoleCode.ADMIN);
            assertThat(revoked.scopeType()).isEqualTo(StaffScopeType.BRANCH);
            assertThat(revoked.branchAdmin()).isTrue();
            mockMvc.perform(get("/api/v1/auth/me").session(organizationAdminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                    .andExpect(jsonPath("$.organizationScope").value("BRANCH"));

            mockMvc.perform(post("/api/v1/branches")
                            .session(organizationAdminSession)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBranchBody(attemptedCode)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                    .andExpect(jsonPath("$.detail").value(ACCESS_DENIED_DETAIL));

            assertNoBusinessEffects(before, attemptedCode);
        } finally {
            jdbcTemplate.update("""
                    update gym.staff_scopes
                    set scope_type = 'ORGANIZATION', version = version + 1
                    where user_id = ? and scope_type = 'BRANCH'
                    """, adminId);
        }
    }

    @Test
    void readsPreserveRoleProjectionsAndSeparateCatalogFromAssignedContext() throws Exception {
        mockMvc.perform(get("/api/v1/organization").session(organizationAdminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.version").exists());
        mockMvc.perform(get("/api/v1/organization").session(branchAdminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").exists())
                .andExpect(jsonPath("$.version").exists());
        mockMvc.perform(get("/api/v1/organization").session(receptionistSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brandName").exists())
                .andExpect(jsonPath("$.legalName").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());

        MvcResult catalog = mockMvc.perform(get("/api/v1/branches")
                        .session(branchAdminSession)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        List<String> catalogIds = JsonPath.read(catalog.getResponse().getContentAsString(), "$.items[*].id");
        assertThat(catalogIds).contains(
                INITIAL_BRANCH_ID.toString(),
                activeUnassignedBranchId.toString(),
                inactiveUnassignedBranchId.toString());

        MvcResult context = mockMvc.perform(get("/api/v1/me/branch-context").session(branchAdminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeType").value("BRANCH"))
                .andExpect(jsonPath("$.activeBranch.id").value(INITIAL_BRANCH_ID.toString()))
                .andReturn();
        List<String> availableBranchIds = JsonPath.read(
                context.getResponse().getContentAsString(), "$.availableBranches[*].id");
        assertThat(availableBranchIds).containsExactly(INITIAL_BRANCH_ID.toString());
    }

    @Test
    void branchContextHttpFlowRejectsUnavailableSelectionAndAutoselectsOnlyAvailableBranch()
            throws Exception {
        mockMvc.perform(get("/api/v1/me/branch-context").session(branchAdminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeBranch.id").value(INITIAL_BRANCH_ID.toString()));

        selectBranch(branchAdminSession, INITIAL_BRANCH_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeBranch.id").value(INITIAL_BRANCH_ID.toString()));

        mockMvc.perform(put("/api/v1/me/branch-context")
                        .session(branchAdminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + activeUnassignedBranchId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAFF_SCOPE_CONFLICT"));

        mockMvc.perform(get("/api/v1/me/branch-context").session(branchAdminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeBranch.id").value(INITIAL_BRANCH_ID.toString()));

        mockMvc.perform(delete("/api/v1/me/branch-context")
                        .session(branchAdminSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(hasSelectedBranch(branchAdminSession)).isFalse();

        mockMvc.perform(get("/api/v1/me/branch-context").session(branchAdminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeBranch.id").value(INITIAL_BRANCH_ID.toString()))
                .andExpect(jsonPath("$.availableBranches.length()").value(1));
        assertThat(hasSelectedBranch(branchAdminSession)).isFalse();

        mockMvc.perform(get("/api/v1/auth/me").session(branchAdminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.organizationScope").value("BRANCH"))
                .andExpect(jsonPath("$.activeBranch.code").value("PRINCIPAL"))
                .andExpect(jsonPath("$.availableBranches[0].code").value("PRINCIPAL"));
    }

    @Test
    void duplicateAndNoOpMutationsDoNotPersistBusinessAuditAndMissingBranchStaysNotFound()
            throws Exception {
        long auditBeforeDuplicate = organizationAuditCount();
        mockMvc.perform(post("/api/v1/branches")
                        .session(organizationAdminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBranchBodyWithPrivateContact(
                                branchCode(activeUnassignedBranchId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GYM_BRANCH_CODE_CONFLICT"));
        assertThat(organizationAuditCount()).isEqualTo(auditBeforeDuplicate);

        BusinessSnapshot beforeNoOp = businessSnapshot();
        mockMvc.perform(put("/api/v1/branches/{id}", activeUnassignedBranchId)
                        .session(organizationAdminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noOpUpdateBody(activeUnassignedBranchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(beforeNoOp.activeBranch().get("version")));
        assertThat(businessSnapshot()).isEqualTo(beforeNoOp);

        UUID missingBranchId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/branches/{id}", missingBranchId)
                        .session(organizationAdminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GYM_BRANCH_NOT_FOUND"));

        BusinessSnapshot beforeDelete = businessSnapshot();
        mockMvc.perform(delete("/api/v1/branches/{id}", activeUnassignedBranchId)
                        .session(organizationAdminSession)
                        .with(csrf()))
                .andExpect(status().is(405));
        assertThat(businessSnapshot()).isEqualTo(beforeDelete);

        long currentVersion = branchVersion(activeUnassignedBranchId);
        BusinessSnapshot beforeStaleUpdate = businessSnapshot();
        mockMvc.perform(put("/api/v1/branches/{id}", activeUnassignedBranchId)
                        .session(organizationAdminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBranchBody("Changed by regression", currentVersion)))
                .andExpect(status().isOk());

        BusinessSnapshot afterAuthorizedUpdate = businessSnapshot();
        assertThat(afterAuthorizedUpdate.activeBranch().get("version"))
                .isEqualTo(((Number) beforeStaleUpdate.activeBranch().get("version")).longValue() + 1);
        long auditAfterAuthorizedUpdate = organizationAuditCount();
        mockMvc.perform(put("/api/v1/branches/{id}", activeUnassignedBranchId)
                        .session(organizationAdminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBranchBody("Stale regression update", currentVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GYM_BRANCH_VERSION_CONFLICT"));
        assertThat(businessSnapshot()).isEqualTo(afterAuthorizedUpdate);
        assertThat(organizationAuditCount()).isEqualTo(auditAfterAuthorizedUpdate);
    }

    private UUID createBranch(MockHttpSession session, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/branches")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBranchBody(code)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private org.springframework.test.web.servlet.ResultActions selectBranch(
            MockHttpSession session, UUID branchId) throws Exception {
        return mockMvc.perform(put("/api/v1/me/branch-context")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":\"" + branchId + "\"}"));
    }

    private MockHttpSession login(String username, String password) throws Exception {
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

    private OrganizationAdminFixture provisionOrganizationAdminWithoutAssignments() {
        UUID id = UUID.randomUUID();
        String username = "unassigned-org-admin-" + id.toString().substring(0, 8);
        String email = username + "@example.test";
        String password = "Unassigned-admin-strong-password";
        jdbcTemplate.update("""
                insert into gym.users
                    (id, username, email, password_hash, first_name, last_name, status)
                values (?, ?, ?, ?, 'Regression', 'Administrator', 'ACTIVE')
                """, id, username, email, passwordEncoder.encode(password));
        jdbcTemplate.update("""
                insert into gym.user_roles (user_id, role_id)
                select ?, id from gym.roles where role_code = 'ADMIN'
                """, id);
        jdbcTemplate.update("""
                insert into gym.staff_scopes (user_id, scope_type, version)
                values (?, 'ORGANIZATION', 0)
                """, id);
        return new OrganizationAdminFixture(id, username, password);
    }

    private BusinessSnapshot businessSnapshot() {
        return new BusinessSnapshot(
                jdbcTemplate.queryForMap("""
                        select legal_name, brand_name, version, updated_at
                        from gym.organizations where is_canonical = true
                        """),
                branchState(INITIAL_BRANCH_ID),
                branchState(activeUnassignedBranchId),
                branchState(inactiveUnassignedBranchId),
                organizationAuditCount());
    }

    private void assertNoBusinessEffects(BusinessSnapshot before, String attemptedCode) {
        assertThat(businessSnapshot()).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.gym_branches where code = ?", Integer.class, attemptedCode))
                .isZero();
    }

    private Map<String, Object> branchState(UUID branchId) {
        return jdbcTemplate.queryForMap("""
                select name, status, version, updated_at
                from gym.gym_branches where id = ?
                """, branchId);
    }

    private String branchCode(UUID branchId) {
        return jdbcTemplate.queryForObject(
                "select code from gym.gym_branches where id = ?", String.class, branchId);
    }

    private long branchVersion(UUID branchId) {
        return jdbcTemplate.queryForObject(
                "select version from gym.gym_branches where id = ?", Long.class, branchId);
    }

    private long organizationAuditCount() {
        return jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where resource_type in ('ORGANIZATION', 'GYM_BRANCH')
                """, Long.class);
    }

    private static boolean hasSelectedBranch(MockHttpSession session) {
        return Collections.list(session.getAttributeNames()).stream()
                .anyMatch(attribute -> attribute.endsWith(".activeBranchId"));
    }

    private static String createBranchBody(String code) {
        return """
                {"code":"%s","name":"Regression %s","countryCode":"SV",
                 "timezone":"America/El_Salvador"}
                """.formatted(code, code);
    }

    private static String createBranchBodyWithPrivateContact(String code) {
        return """
                {"code":"%s","name":"Regression %s","addressLine1":"Private Address Marker 987",
                 "countryCode":"SV","phone":"+50322223333","email":"audit-private@example.test"}
                """.formatted(code, code);
    }

    private String noOpUpdateBody(UUID branchId) {
        String code = branchCode(branchId);
        return """
                {"name":"Regression %s","countryCode":"SV",
                 "timezone":"America/El_Salvador","version":%d}
                """.formatted(code, branchVersion(branchId));
    }

    private static String updateBranchBody(String name, long version) {
        return """
                {"name":"%s","countryCode":"SV",
                 "timezone":"America/El_Salvador","version":%d}
                """.formatted(name, version);
    }

    private static String statusBody(String reason, long version) {
        return """
                {"reason":"%s","version":%d}
                """.formatted(reason, version);
    }

    private static String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 10).toUpperCase(java.util.Locale.ROOT);
    }

    private record OrganizationAdminFixture(UUID id, String username, String password) {}

    private record BusinessSnapshot(
            Map<String, Object> organization,
            Map<String, Object> initialBranch,
            Map<String, Object> activeBranch,
            Map<String, Object> inactiveBranch,
            long auditCount) {}
}
