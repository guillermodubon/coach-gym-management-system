package io.github.guillermodubon.coachgym.maintenance;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** Contract tests for the public equipment-incident OpenAPI document. */
class IncidentOpenApiIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Test
    void documentsAllIncidentPathsAndOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents'].post").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents'].get").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents/{id}'].get").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents/{id}/history'].get").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents/{id}/start'].post").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents/{id}/priority'].post").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents/{id}/resolve'].post").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents/{id}'].put").doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents/{id}'].delete").doesNotExist());
    }

    @Test
    void documentsReportingRolesCsrfAndOptimisticLocking() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents'].post.description",
                        containsString("ADMIN")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents'].post.description",
                        containsString("RECEPTIONIST")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents'].post.description",
                        containsString("CSRF")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents/{id}/start'].post.description",
                        containsString("ADMIN only")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents/{id}/start'].post.description",
                        containsString("optimistic-lock")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents/{id}/resolve'].post.description",
                        containsString("IN_PROGRESS to RESOLVED")));
    }

    @Test
    void documentsReportRequestWithoutServerControlledFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.ReportIncidentRequest.required",
                        hasItems("equipmentId", "priority", "description")))
                .andExpect(jsonPath(
                        "$.components.schemas.ReportIncidentRequest.properties.description.maxLength")
                        .value(2000))
                .andExpect(jsonPath(
                        "$.components.schemas.ReportIncidentRequest.properties.equipmentVersion.minimum")
                        .value(0))
                .andExpect(jsonPath(
                        "$.components.schemas.ReportIncidentRequest.properties.status")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.ReportIncidentRequest.properties.incidentCode")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.ReportIncidentRequest.properties.reportedByUserId")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.ReportIncidentRequest.properties.reportedAt")
                        .doesNotExist());
    }

    @Test
    void documentsIncidentEnumsAndMutationVersions() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.ReportIncidentRequest.properties.priority.enum",
                        hasItems("LOW", "MEDIUM", "HIGH", "CRITICAL")))
                .andExpect(jsonPath(
                        "$.components.schemas.IncidentResponse.properties.status.enum",
                        hasItems("OPEN", "IN_PROGRESS", "RESOLVED")))
                .andExpect(jsonPath(
                        "$.components.schemas.StartIncidentInvestigationRequest.required",
                        hasItems("reason", "version")))
                .andExpect(jsonPath(
                        "$.components.schemas.ChangeIncidentPriorityRequest.required",
                        hasItems("priority", "reason", "version")))
                .andExpect(jsonPath(
                        "$.components.schemas.ResolveIncidentRequest.required",
                        hasItems("resolutionNotes", "version")));
    }

    @Test
    void doesNotDocumentMaintenanceAsAStaffRole() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents'].post.description",
                        not(containsString("ROLE_MAINTENANCE"))))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/incidents'].post.description",
                        not(containsString("MAINTENANCE role"))));
    }
}
