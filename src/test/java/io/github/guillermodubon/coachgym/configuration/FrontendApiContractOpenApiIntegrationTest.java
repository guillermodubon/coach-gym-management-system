package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Verifies that the generated OpenAPI document is a complete, safe frontend
 * inventory rather than an informal list of selected controllers.
 */
class FrontendApiContractOpenApiIntegrationTest extends AbstractIncidentApiIntegrationTest {

    @Test
    void inventoriesEveryPublicModuleAndUsesOnlyVersionedApplicationPaths() throws Exception {
        String document = openApiDocument();
        Map<String, Object> paths = JsonPath.read(document, "$.paths");

        assertThat(paths.keySet()).contains(
                "/api/v1/auth/csrf",
                "/api/v1/clients",
                "/api/v1/plans",
                "/api/v1/promotions",
                "/api/v1/memberships",
                "/api/v1/payments",
                "/api/v1/payment-attempts/stripe-checkout",
                "/api/v1/payment-provider/stripe/webhook",
                "/api/v1/payments/{paymentId}/receipt.pdf",
                "/api/v1/clients/{clientId}/access-credential",
                "/api/v1/access/check-in",
                "/api/v1/settings/access-payment-policy",
                "/api/v1/email-deliveries",
                "/api/v1/notifications",
                "/api/v1/audit-entries",
                "/api/v1/audit-entries/export.csv",
                "/api/v1/reporting/dashboard",
                "/api/v1/equipment-categories",
                "/api/v1/equipment",
                "/api/v1/incidents",
                "/api/v1/maintenances");
        assertThat(paths.keySet())
                .allMatch(path -> path.startsWith("/api/v1/"));
    }

    @Test
    void exposesBothSecuritySchemesAndProtectsEveryNonPublicBrowserBoundary() throws Exception {
        String document = openApiDocument();
        Map<String, Object> schemes = JsonPath.read(
                document, "$.components.securitySchemes");

        assertThat(schemes).containsKeys("sessionCookie", "stripeWebhookSignature");
        assertSessionSecurity(document, "/api/v1/auth/me", "get");
        assertSessionSecurity(document, "/api/v1/auth/logout", "post");
        assertSessionSecurity(document, "/api/v1/clients", "post");
        assertSessionSecurity(document, "/api/v1/plans", "get");
        assertSessionSecurity(document, "/api/v1/promotions", "get");
        assertSessionSecurity(document, "/api/v1/memberships/{id}", "get");
        assertSessionSecurity(document, "/api/v1/payments", "get");
        assertSessionSecurity(document, "/api/v1/audit-entries", "get");
        Object webhookSecurity = JsonPath.read(
                document,
                "$.paths['/api/v1/payment-provider/stripe/webhook'].post"
                        + ".security[0].stripeWebhookSignature");
        assertThat((Object) webhookSecurity)
                .isNotNull();

        Map<String, Object> loginOperation = JsonPath.read(
                document, "$.paths['/api/v1/auth/login'].post");
        assertThat(loginOperation).doesNotContainKey("security");
    }

    @Test
    void documentsCommonPageShapeAndDoesNotExposeInternalOrUnsupportedContracts()
            throws Exception {
        String document = openApiDocument();

        for (String schema : Set.of(
                "PlanPageResponse",
                "PromotionPageResponse",
                "AuditEntryPageResponse")) {
            for (String property : Set.of(
                    "items", "page", "size", "totalElements", "totalPages")) {
                Object schemaProperty = JsonPath.read(
                        document,
                        "$.components.schemas." + schema
                                + ".properties." + property);
                assertThat((Object) schemaProperty)
                        .as("schema %s property %s", schema, property)
                        .isNotNull();
            }
        }

        assertThat(document)
                .doesNotContain("ROLE_MAINTENANCE", "passwordHash", "secretKey", "serviceRoleKey", "storageKey");
    }

    @Test
    void exposesStableProblemDetailFieldsAndCorrelationHeaderToTheFrontend()
            throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-Correlation-ID", "ui-contract-001"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-ID", "ui-contract-001"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/me"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.correlationId").value("ui-contract-001"))
                .andExpect(jsonPath("$.detail").value("Authentication is required."));
    }

    @Test
    void generatesTheSameBoundedCorrelationIdInHeaderAndProblemBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String headerValue = result.getResponse().getHeader("X-Correlation-ID");
        String bodyValue = JsonPath.read(
                result.getResponse().getContentAsString(), "$.correlationId");

        assertThat(headerValue)
                .isNotBlank()
                .hasSize(36)
                .matches("[0-9a-f-]+");
        assertThat(bodyValue).isEqualTo(headerValue);
    }

    private String openApiDocument() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private static void assertSessionSecurity(
            String document,
            String path,
            String method) {
        Object security = JsonPath.read(
                document,
                "$.paths['" + path + "']." + method
                        + ".security[0].sessionCookie");
        assertThat((Object) security)
                .as("session security for %s %s", method, path)
                .isNotNull();
    }
}
