package io.github.guillermodubon.coachgym.notification;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Final OpenAPI contract for the authenticated internal notification inbox. */
class NotificationOpenApiIntegrationTest
        extends AbstractNotificationApiIntegrationTest {

    @Test
    void documentsAllNotificationInboxOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/notifications/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/notifications/unread-count'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/notifications/{id}/read'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/notifications/read-all'].post").exists());
    }

    @Test
    void doesNotDocumentUnsupportedNotificationMutations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].post").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].put").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].delete").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/notifications/{id}'].put").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/notifications/{id}'].delete").doesNotExist());
    }

    @Test
    void documentsSessionSecurityAndReadMutationRequirements() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie").exists())
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.type")
                        .value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.in")
                        .value("cookie"))
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get.security[0]")
                        .value(hasKey("sessionCookie")))
                .andExpect(jsonPath("$.paths['/api/v1/notifications/{id}/read'].post.security[0]")
                        .value(hasKey("sessionCookie")))
                .andExpect(jsonPath("$.paths['/api/v1/notifications/read-all'].post.security[0]")
                        .value(hasKey("sessionCookie")));
    }

    @Test
    void documentsInboxFiltersAndPagination() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get.parameters[*].name")
                        .value(containsInAnyOrder(
                                "read",
                                "type",
                                "severity",
                                "resourceType",
                                "createdFrom",
                                "createdUntil",
                                "page",
                                "size",
                                "sort",
                                "direction")))
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get.parameters[?(@.name == 'page')].schema.default")
                        .value(hasItem(0)))
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get.parameters[?(@.name == 'size')].schema.default")
                        .value(hasItem(25)));
    }

    @Test
    void documentsNotificationEnumsAndReadModel() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.NotificationResponse.properties.notificationType.enum")
                        .value(containsInAnyOrder(
                                "MEMBERSHIP_EXPIRING",
                                "PAYMENT_VOIDED",
                                "PAYMENT_REFUNDED",
                                "INCIDENT_ASSIGNED",
                                "MAINTENANCE_ASSIGNED",
                                "SYSTEM")))
                .andExpect(jsonPath("$.components.schemas.NotificationResponse.properties.severity.enum")
                        .value(containsInAnyOrder("INFO", "WARNING", "CRITICAL")))
                .andExpect(jsonPath("$.components.schemas.NotificationResponse.properties.resourceType.enum")
                        .value(containsInAnyOrder(
                                "INCIDENT",
                                "MAINTENANCE",
                                "MEMBERSHIP",
                                "PAYMENT",
                                "SYSTEM")))
                .andExpect(jsonPath("$.components.schemas.NotificationResponse.properties.read.type")
                        .value("boolean"))
                .andExpect(jsonPath("$.components.schemas.NotificationResponse.properties.readAt.format")
                        .value("date-time"))
                .andExpect(jsonPath("$.components.schemas.NotificationUnreadCountResponse.properties.count.minimum")
                        .value(0));
    }

    @Test
    void documentsCurrentRolesWithoutReintroducingMaintenanceRole() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get.description")
                        .value(containsString("ADMIN")))
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get.description")
                        .value(containsString("RECEPTIONIST")))
                .andExpect(jsonPath("$").value(
                        not(containsString("ROLE_MAINTENANCE"))))
                .andExpect(jsonPath("$").value(
                        not(containsString("hasRole('MAINTENANCE')"))));
    }
}
