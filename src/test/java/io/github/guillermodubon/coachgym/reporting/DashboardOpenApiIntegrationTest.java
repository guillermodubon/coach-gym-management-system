package io.github.guillermodubon.coachgym.reporting;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Final OpenAPI contract for the role-aware operational dashboard. */
class DashboardOpenApiIntegrationTest
        extends AbstractDashboardApiIntegrationTest {

    @Test
    void documentsTheOperationalDashboardGetOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].get")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].get.summary")
                        .value("Get the operational dashboard"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].get.security[0]")
                        .value(hasKey("sessionCookie")));
    }

    @Test
    void doesNotDocumentUnsupportedDashboardMutations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].post")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].put")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].patch")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].delete")
                        .doesNotExist());
    }

    @Test
    void documentsOptionalInclusiveDateParameters() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].get"
                                + ".parameters[*].name")
                        .value(containsInAnyOrder("from", "until")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].get"
                                + ".parameters[?(@.name == 'from')].required")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].get"
                                + ".parameters[?(@.name == 'until')].required")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].get"
                                + ".parameters[?(@.name == 'from')]"
                                + ".schema.format")
                        .value(org.hamcrest.Matchers.hasItem("date")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].get"
                                + ".parameters[?(@.name == 'until')]"
                                + ".schema.format")
                        .value(org.hamcrest.Matchers.hasItem("date")));
    }

    @Test
    void documentsTheCompleteDashboardResponseModel() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.OperationalDashboardResponse"
                                + ".properties.generatedAt.format")
                        .value("date-time"))
                .andExpect(jsonPath(
                        "$.components.schemas.OperationalDashboardResponse"
                                + ".properties.period.$ref")
                        .value("#/components/schemas/DashboardPeriodResponse"))
                .andExpect(jsonPath(
                        "$.components.schemas.OperationalDashboardResponse"
                                + ".properties.memberships.$ref")
                        .value("#/components/schemas/MembershipDashboardResponse"))
                .andExpect(jsonPath(
                        "$.components.schemas.OperationalDashboardResponse"
                                + ".properties.access.$ref")
                        .value("#/components/schemas/AccessDashboardResponse"))
                .andExpect(jsonPath(
                        "$.components.schemas.OperationalDashboardResponse"
                                + ".properties.notifications.$ref")
                        .value("#/components/schemas/DashboardNotificationResponse"));
    }

    @Test
    void documentsFinancialAndUnreadMetricConstraints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.PaymentDashboardResponse"
                                + ".properties.registeredAmount.minimum")
                        .value(0.00))
                .andExpect(jsonPath(
                        "$.components.schemas.PaymentDashboardResponse"
                                + ".properties.currency.pattern")
                        .value("[A-Z]{3}"))
                .andExpect(jsonPath(
                        "$.components.schemas.DashboardNotificationResponse"
                                + ".properties.unread.minimum")
                        .value(0));
    }

    @Test
    void documentsCurrentRoleProjectionAndReadOnlyBehavior() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].get.description")
                        .value(containsString("ADMIN")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].get.description")
                        .value(containsString("RECEPTIONIST")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].get.description")
                        .value(containsString("read-only")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].get.description")
                        .value(containsString("does not require CSRF")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reporting/dashboard'].get.description")
                        .value(not(containsString("ROLE_MAINTENANCE"))));
    }
}
