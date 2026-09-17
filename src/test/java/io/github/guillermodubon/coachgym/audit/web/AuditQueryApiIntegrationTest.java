package io.github.guillermodubon.coachgym.audit.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** HTTP contract and security tests for the ADMIN audit query boundary. */
class AuditQueryApiIntegrationTest extends AbstractIncidentApiIntegrationTest {

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
                .andExpect(jsonPath("$.paths['/api/v1/audit-entries'].post").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/audit-entries'].get.parameters[*].name")
                        .value(containsInAnyOrder(
                                "actorUserId", "actorIdentifier", "actionCode", "resourceType",
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
                .andExpect(content().string(
                        containsString("CSV export is deferred outside the current scope")))
                .andExpect(content().string(not(containsString("rawMetadata"))));
    }
}
