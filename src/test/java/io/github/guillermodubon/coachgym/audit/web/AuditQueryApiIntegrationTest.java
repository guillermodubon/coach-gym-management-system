package io.github.guillermodubon.coachgym.audit.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

/** HTTP contract and security tests for the ADMIN audit query boundary. */
class AuditQueryApiIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final UUID INITIAL_BRANCH_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000002");
    private UUID entryId;

    @BeforeEach
    void insertAuditQueryFixture() {
        entryId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.audit_entries
                    (id, actor_user_id, actor_identifier_snapshot,
                     action_code, resource_type, resource_id,
                     resource_code_snapshot, summary, metadata)
                values (?, ?, ?, 'CLIENT_REGISTERED', 'CLIENT', ?, ?, ?,
                        '{"password":"must-not-return"}'::jsonb)
                """,
                entryId,
                adminId,
                ADMIN_USERNAME,
                UUID.randomUUID(),
                "CLI-AUDIT-HTTP-001",
                "Client registered for HTTP audit-query test");
    }

    @Test
    void anonymousListAndDetailRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/audit-entries"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/api/v1/audit-entries/{id}", entryId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void adminCanListAndRetrieveOnlySanitizedAuditDataWithoutCsrf() throws Exception {
        mockMvc.perform(get("/api/v1/audit-entries")
                        .session(loginAsAdmin())
                        .param("actionCode", "client_registered")
                        .param("page", "0")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[?(@.id == '%s')]".formatted(entryId))
                        .isNotEmpty())
                .andExpect(jsonPath("$.items[0].metadata").doesNotExist())
                .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(get("/api/v1/audit-entries/{id}", entryId)
                        .session(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entryId.toString()))
                .andExpect(jsonPath("$.metadata").isMap())
                .andExpect(jsonPath("$.metadata").isEmpty())
                .andExpect(jsonPath("$.metadataRedacted").value(true))
                .andExpect(content().string(not(containsString("must-not-return"))))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void adminCanDownloadABoundedSanitizedCsvWithoutCsrf() throws Exception {
        mockMvc.perform(get("/api/v1/audit-entries/export.csv")
                        .session(loginAsAdmin())
                        .param("occurredFrom", "2000-01-01T00:00:00Z")
                        .param("occurredUntil", "2000-01-31T00:00:00Z")
                        .param("branchIds", INITIAL_BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.matchesPattern(
                        "attachment; filename=\\\"audit-export-[0-9]{8}-[0-9]{6}Z\\.csv\\\"")))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string(
                        "entry_id,occurred_at,actor_user_id,actor_identifier,action_code,"
                                + "resource_type,resource_id,resource_code,summary,correlation_id,branch_id,metadata\r\n"));
    }

    @Test
    void adminExportStreamsCurrentRowsAndNeverReturnsRawMetadata() throws Exception {
        Instant now = Instant.now();
        mockMvc.perform(get("/api/v1/audit-entries/export.csv")
                        .session(loginAsAdmin())
                        .param("occurredFrom", now.minusSeconds(60).toString())
                        .param("occurredUntil", now.plusSeconds(60).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(entryId.toString())))
                .andExpect(content().string(containsString("CLIENT_REGISTERED")))
                .andExpect(content().string(not(containsString("must-not-return"))))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void exportSecurityAndRangeErrorsAreStable() throws Exception {
        mockMvc.perform(get("/api/v1/audit-entries/export.csv")
                        .param("occurredFrom", "2000-01-01T00:00:00Z")
                        .param("occurredUntil", "2000-01-02T00:00:00Z"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/api/v1/audit-entries/export.csv")
                        .session(loginAsReceptionist())
                        .param("occurredFrom", "2000-01-01T00:00:00Z")
                        .param("occurredUntil", "2000-01-02T00:00:00Z"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/audit-entries/export.csv")
                        .session(loginAsAdmin())
                        .param("occurredFrom", "2000-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_EXPORT_RANGE_REQUIRED"));

        mockMvc.perform(get("/api/v1/audit-entries/export.csv")
                        .session(loginAsAdmin())
                        .param("occurredFrom", "2000-01-01T00:00:00Z")
                        .param("occurredUntil", "2000-03-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_EXPORT_RANGE_TOO_LARGE"));

        mockMvc.perform(get("/api/v1/audit-entries/export.csv")
                        .session(loginAsAdmin())
                        .param("occurredFrom", "2000-01-01T00:00:00Z")
                        .param("occurredUntil", "2000-01-02T00:00:00Z")
                        .param("sort", "metadata"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_EXPORT_VALIDATION_FAILED"));
    }

    @Test
    void receptionistCannotListOrRetrieveAuditHistory() throws Exception {
        mockMvc.perform(get("/api/v1/audit-entries")
                        .session(loginAsReceptionist()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/audit-entries/{id}", entryId)
                        .session(loginAsReceptionist()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void branchAdminIsLimitedToPersistedActiveAssignmentsForListAndDetails() throws Exception {
        String username = "audit-branch-" + UUID.randomUUID();
        String password = "Safe-Test-Password-123!";
        UUID branchAdminId = provisionUser(
                username, username + "@example.test", password, "ADMIN");
        jdbcTemplate.update(
                "update gym.staff_scopes set scope_type='BRANCH', version=version+1 where user_id=?",
                branchAdminId);

        UUID assignedEntryId = UUID.randomUUID();
        UUID unassignedEntryId = UUID.randomUUID();
        insertBranchAuditEntry(assignedEntryId, INITIAL_BRANCH_ID);
        insertBranchAuditEntry(unassignedEntryId, UUID.randomUUID());

        mockMvc.perform(get("/api/v1/audit-entries")
                        .session(loginAsAdmin())
                        .param("branchIds", INITIAL_BRANCH_ID.toString())
                        .param("result", "DENIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(
                        org.hamcrest.Matchers.hasItem(assignedEntryId.toString())))
                .andExpect(jsonPath("$.items[*].id").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.hasItem(unassignedEntryId.toString()))));
        MockHttpSession session = loginBranchAdmin(username, password);

        mockMvc.perform(get("/api/v1/audit-entries")
                        .session(session)
                        .param("branchIds", INITIAL_BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(
                        org.hamcrest.Matchers.hasItem(assignedEntryId.toString())))
                .andExpect(jsonPath("$.items[*].id").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.hasItem(unassignedEntryId.toString()))));

        mockMvc.perform(get("/api/v1/audit-entries")
                        .session(session)
                        .param("branchIds", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/audit-entries").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/audit-entries/{id}", assignedEntryId)
                        .session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit-entries/{id}", unassignedEntryId)
                        .session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUDIT_ENTRY_NOT_FOUND"));
    }

    @Test
    void branchAdminExportsOnlyAssignedBranchRowsAndUnauthorizedScopeNeverStreams()
            throws Exception {
        String username = "audit-export-branch-" + UUID.randomUUID();
        String password = "Safe-Test-Password-123!";
        UUID branchAdminId = provisionUser(
                username, username + "@example.test", password, "ADMIN");
        jdbcTemplate.update(
                "update gym.staff_scopes set scope_type='BRANCH', version=version+1 where user_id=?",
                branchAdminId);
        MockHttpSession session = loginBranchAdmin(username, password);
        UUID assignedEntryId = UUID.randomUUID();
        UUID unassignedEntryId = UUID.randomUUID();
        insertBranchAuditEntry(assignedEntryId, INITIAL_BRANCH_ID);
        insertBranchAuditEntry(unassignedEntryId, UUID.randomUUID());
        Long exportsBefore = jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where action_code = 'AUDIT_ENTRIES_EXPORTED'
                """, Long.class);

        mockMvc.perform(get("/api/v1/audit-entries/export.csv")
                        .session(session)
                        .param("occurredFrom", "2000-01-01T00:00:00Z")
                        .param("occurredUntil", "2000-01-02T00:00:00Z"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Content-Disposition"));

        mockMvc.perform(get("/api/v1/audit-entries/export.csv")
                        .session(session)
                        .param("occurredFrom", "2000-01-01T00:00:00Z")
                        .param("occurredUntil", "2000-01-02T00:00:00Z")
                        .param("branchIds", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Content-Disposition"));

        Long exportsAfter = jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where action_code = 'AUDIT_ENTRIES_EXPORTED'
                """, Long.class);
        org.assertj.core.api.Assertions.assertThat(exportsAfter).isEqualTo(exportsBefore);

        Instant now = Instant.now();
        var exported = mockMvc.perform(get("/api/v1/audit-entries/export.csv")
                        .session(session)
                        .param("occurredFrom", now.minusSeconds(60).toString())
                        .param("occurredUntil", now.plusSeconds(60).toString())
                        .param("result", "DENIED")
                        .param("branchIds", INITIAL_BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andReturn();
        String csv = exported.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(csv)
                .contains(assignedEntryId.toString(), INITIAL_BRANCH_ID.toString())
                .doesNotContain(unassignedEntryId.toString(), entryId.toString());

        Map<String, Object> exportMetadata = jdbcTemplate.queryForMap("""
                select metadata
                from gym.audit_entries
                where action_code = 'AUDIT_ENTRIES_EXPORTED'
                  and actor_user_id = ?
                order by occurred_at desc
                limit 1
                """, branchAdminId);
        String metadata = exportMetadata.get("metadata").toString();
        org.assertj.core.api.Assertions.assertThat(metadata)
                .contains("BRANCH", INITIAL_BRANCH_ID.toString())
                .doesNotContain(unassignedEntryId.toString());
    }

    @Test
    void invalidFiltersAreRejectedAndUnknownEntriesReturnNotFound() throws Exception {
        var admin = loginAsAdmin();

        mockMvc.perform(get("/api/v1/audit-entries")
                        .session(admin)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_QUERY_VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/audit-entries")
                        .session(admin)
                        .param("actionCode", "NOT_ALLOWLISTED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_QUERY_VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/audit-entries")
                        .session(admin)
                        .param("occurredFrom", "not-an-instant"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_QUERY_VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/audit-entries/{id}", UUID.randomUUID())
                        .session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUDIT_ENTRY_NOT_FOUND"));
    }

    @Test
    void auditHistoryExposesNoMutationOperations() throws Exception {
        mockMvc.perform(post("/api/v1/audit-entries")
                        .session(loginAsAdmin())
                        .with(csrf()))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/api/v1/audit-entries/{id}", entryId)
                        .session(loginAsAdmin())
                        .with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void openApiDocumentsAdminOnlyReadOnlyQueryContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/audit-entries'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/audit-entries/{auditEntryId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/audit-entries/export.csv'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/audit-entries/export.csv'].get.parameters[*].name")
                        .value(containsInAnyOrder(
                                "actorUserId", "actorIdentifier", "actionCode", "resourceType",
                                "result", "branchIds", "resourceId", "resourceCode",
                                "correlationId", "occurredFrom", "occurredUntil", "sort", "direction")))
                .andExpect(jsonPath("$.paths['/api/v1/audit-entries/export.csv'].get.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/audit-entries/export.csv'].get.responses['200']"
                        + ".content['text/csv']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/audit-entries'].post").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/audit-entries'].get.parameters[*].name")
                        .value(containsInAnyOrder(
                                "actorUserId", "actorIdentifier", "actionCode", "resourceType",
                                "result", "branchIds",
                                "resourceId", "resourceCode", "correlationId", "occurredFrom",
                                "occurredUntil", "page", "size", "sort", "direction")))
                .andExpect(jsonPath("$.paths['/api/v1/audit-entries'].get.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/audit-entries/{auditEntryId}'].get.responses['404']")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.AuditEntryPageResponse.properties.items")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.AuditEntryDetailsResponse.properties.metadata")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.AuditEntryDetailsResponse.properties.metadataRedacted")
                        .exists())
                .andExpect(content().string(containsString("default-deny sanitized")))
                .andExpect(content().string(containsString("bounded CSV export")))
                .andExpect(content().string(containsString("occurredFrom")))
                .andExpect(content().string(containsString("text/csv")))
                .andExpect(content().string(containsString("ADMIN + ORGANIZATION")))
                .andExpect(content().string(containsString("actively assigned branches")))
                .andExpect(content().string(containsString("branch_id")))
                .andExpect(content().string(containsString("cannot appear in its own export")))
                .andExpect(content().string(not(containsString("rawMetadata"))));
    }

    @Test
    void csvRejectsInvalidSessionsInactiveAdminsAndMissingPersistedScope() throws Exception {
        mockMvc.perform(get("/api/v1/audit-entries/export.csv")
                        .session(new MockHttpSession())
                        .param("occurredFrom", "2000-01-01T00:00:00Z")
                        .param("occurredUntil", "2000-01-02T00:00:00Z"))
                .andExpect(status().isUnauthorized());

        String statusUsername = "audit-csv-status-" + UUID.randomUUID();
        UUID statusAdminId = provisionUser(
                statusUsername,
                statusUsername + "@example.test",
                "Safe-Test-Password-123!",
                "ADMIN");
        MockHttpSession suspendedSession = loginStaff(
                statusUsername, "Safe-Test-Password-123!");
        jdbcTemplate.update("""
                update gym.users
                set status='SUSPENDED', security_version=security_version+1
                where id=?
                """, statusAdminId);
        mockMvc.perform(get("/api/v1/audit-entries/export.csv")
                        .session(suspendedSession)
                        .param("occurredFrom", "2000-01-01T00:00:00Z")
                        .param("occurredUntil", "2000-01-02T00:00:00Z"))
                .andExpect(status().isUnauthorized());

        jdbcTemplate.update("""
                update gym.users
                set status='ACTIVE', security_version=security_version+1
                where id=?
                """, statusAdminId);
        MockHttpSession deactivatedSession = loginStaff(
                statusUsername, "Safe-Test-Password-123!");
        jdbcTemplate.update("""
                update gym.users
                set status='DEACTIVATED', security_version=security_version+1
                where id=?
                """, statusAdminId);
        mockMvc.perform(get("/api/v1/audit-entries/export.csv")
                        .session(deactivatedSession)
                        .param("occurredFrom", "2000-01-01T00:00:00Z")
                        .param("occurredUntil", "2000-01-02T00:00:00Z"))
                .andExpect(status().isUnauthorized());

        String missingScopeUsername = "audit-csv-no-scope-" + UUID.randomUUID();
        provisionAdminWithoutScope(
                missingScopeUsername, "Safe-Test-Password-123!");
        MockHttpSession missingScopeSession = loginStaff(
                missingScopeUsername, "Safe-Test-Password-123!");
        mockMvc.perform(get("/api/v1/audit-entries/export.csv")
                        .session(missingScopeSession)
                        .param("occurredFrom", "2000-01-01T00:00:00Z")
                        .param("occurredUntil", "2000-01-02T00:00:00Z"))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession loginBranchAdmin(String username, String password)
            throws Exception {
        return loginStaff(username, password);
    }

    private MockHttpSession loginStaff(String username, String password)
            throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void provisionAdminWithoutScope(String username, String password) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.users
                    (id, username, email, password_hash, first_name, last_name, status)
                values (?, ?, ?, ?, 'Audit', 'Test', 'ACTIVE')
                """, userId, username, username + "@example.test",
                passwordEncoder.encode(password));
        jdbcTemplate.update("""
                insert into gym.user_roles (user_id, role_id)
                select ?, id from gym.roles where role_code = 'ADMIN'
                """, userId);
    }

    private void insertBranchAuditEntry(UUID auditId, UUID branchId) {
        jdbcTemplate.update("""
                insert into gym.audit_entries
                    (id, actor_user_id, actor_identifier_snapshot,
                     action_code, resource_type, resource_id,
                     resource_code_snapshot, summary, metadata)
                values (?, ?, ?, 'ACCESS_DENIED', 'ACCESS_RECORD', ?, ?, ?,
                        cast(? as jsonb))
                """, auditId, adminId, ADMIN_USERNAME, UUID.randomUUID(),
                "ACCESS-" + auditId,
                "Access attempt at a branch.",
                "{\"branchId\":\"%s\",\"result\":\"DENIED\"}".formatted(branchId));
    }
}
