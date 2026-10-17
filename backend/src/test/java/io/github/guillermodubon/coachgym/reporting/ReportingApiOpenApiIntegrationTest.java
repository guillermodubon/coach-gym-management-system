package io.github.guillermodubon.coachgym.reporting;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** OpenAPI contract for the finalized scope-aware reporting routes. */
class ReportingApiOpenApiIntegrationTest extends AbstractDashboardApiIntegrationTest {

    @Test
    void documentsAllReadOnlyReportingEndpointsWithSessionAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/reporting/summary'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reporting/financial-trend'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reporting/access-trend'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reporting/branches/comparison'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reporting/summary'].get.security[0]")
                        .value(hasKey("sessionCookie")))
                .andExpect(jsonPath("$.paths['/api/v1/reporting/summary'].post").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/reporting/branches/comparison'].post")
                        .doesNotExist());
    }

    @Test
    void documentsHalfOpenDatesScopeAndReceptionistProjectionRules() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/summary'].get.parameters[*].name")
                        .value(containsInAnyOrder(
                                "scope", "branchIds", "fromInclusive", "toExclusive")))
                .andExpect(jsonPath("$.paths['/api/v1/reporting/summary'].get.description")
                        .value(containsString("half-open [fromInclusive, toExclusive)")))
                .andExpect(jsonPath("$.paths['/api/v1/reporting/summary'].get.description")
                        .value(containsString("Receptionists receive membership and access summaries only")))
                .andExpect(jsonPath("$.paths['/api/v1/reporting/summary'].get.description")
                        .value(containsString("filters, never authority")))
                .andExpect(jsonPath("$.paths['/api/v1/reporting/financial-trend'].get.description")
                        .value(containsString("ADMIN only")))
                .andExpect(jsonPath("$.paths['/api/v1/reporting/access-trend'].get.description")
                        .value(containsString("RECEPTIONIST is denied")))
                .andExpect(jsonPath("$.paths['/api/v1/reporting/branches/comparison'].get.description")
                        .value(containsString("between 2 and 20")))
                .andExpect(jsonPath("$.paths['/api/v1/reporting/branches/comparison'].get.description")
                        .value(containsString("only actively assigned branches")));
    }

    @Test
    void documentsTheRoleSpecificDashboardResponseVariants() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ReportingDashboardResponse.oneOf")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ReportingAdministratorDashboardResponse.properties.metrics")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ReportingReceptionistDashboardResponse.properties.memberships")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ReportingReceptionistDashboardResponse.properties.access")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ReportingApiContextResponse.properties.timezone")
                        .exists());
    }
}
