package io.github.guillermodubon.coachgym.maintenance;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** Contract tests for the public maintenance work-order OpenAPI document. */
class MaintenanceOpenApiIntegrationTest
        extends AbstractMaintenanceApiIntegrationTest {

    @Test
    void documentsAllMaintenancePathsAndOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances'].post").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances'].get").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances/{id}'].get").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances/{id}'].put").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances/{id}/history'].get").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances/{id}/start'].post").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances/{id}/complete'].post").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances/{id}/cancel'].post").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances/{id}'].delete")
                        .doesNotExist());
    }

    @Test
    void documentsSessionSecurityForEveryMaintenanceOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances'].post.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances'].get.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances/{id}'].get.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances/{id}'].put.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances/{id}/history'].get.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances/{id}/start'].post.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances/{id}/complete'].post.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances/{id}/cancel'].post.security[0].sessionCookie")
                        .exists());
    }

    @Test
    void documentsScheduleRequestConstraintsAndExcludesServerFields()
            throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.ScheduleMaintenanceRequest.required",
                        hasItems(
                                "equipmentId",
                                "maintenanceType",
                                "scheduledOn",
                                "currency")))
                .andExpect(jsonPath(
                        "$.components.schemas.ScheduleMaintenanceRequest.properties.maintenanceType.enum",
                        containsInAnyOrder("PREVENTIVE", "CORRECTIVE")))
                .andExpect(jsonPath(
                        "$.components.schemas.ScheduleMaintenanceRequest.properties.estimatedCost.minimum")
                        .value(0.0))
                .andExpect(jsonPath(
                        "$.components.schemas.ScheduleMaintenanceRequest.properties.currency.pattern")
                        .value("(?i)[A-Z]{3}"))
                .andExpect(jsonPath(
                        "$.components.schemas.ScheduleMaintenanceRequest.properties.providerName.maxLength")
                        .value(160))
                .andExpect(jsonPath(
                        "$.components.schemas.ScheduleMaintenanceRequest.properties.technicianName.maxLength")
                        .value(160))
                .andExpect(jsonPath(
                        "$.components.schemas.ScheduleMaintenanceRequest.properties.notes.maxLength")
                        .value(2000))
                .andExpect(jsonPath(
                        "$.components.schemas.ScheduleMaintenanceRequest.properties.id")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.ScheduleMaintenanceRequest.properties.maintenanceCode")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.ScheduleMaintenanceRequest.properties.status")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.ScheduleMaintenanceRequest.properties.createdByUserId")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.ScheduleMaintenanceRequest.properties.version")
                        .doesNotExist());
    }

    @Test
    void documentsLifecycleEnumsAndOptimisticLockVersions() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.MaintenanceResponse.properties.status.enum",
                        containsInAnyOrder(
                                "SCHEDULED",
                                "IN_PROGRESS",
                                "COMPLETED",
                                "CANCELLED")))
                .andExpect(jsonPath(
                        "$.components.schemas.MaintenanceResponse.properties.maintenanceType.enum",
                        containsInAnyOrder("PREVENTIVE", "CORRECTIVE")))
                .andExpect(jsonPath(
                        "$.components.schemas.CompleteMaintenanceRequest.properties.equipmentOutcome.enum",
                        containsInAnyOrder("AVAILABLE", "OUT_OF_SERVICE")))
                .andExpect(jsonPath(
                        "$.components.schemas.UpdateScheduledMaintenanceRequest.properties.version.minimum")
                        .value(0))
                .andExpect(jsonPath(
                        "$.components.schemas.StartMaintenanceRequest.properties.maintenanceVersion.minimum")
                        .value(0))
                .andExpect(jsonPath(
                        "$.components.schemas.StartMaintenanceRequest.properties.equipmentVersion.minimum")
                        .value(0))
                .andExpect(jsonPath(
                        "$.components.schemas.CompleteMaintenanceRequest.properties.maintenanceVersion.minimum")
                        .value(0))
                .andExpect(jsonPath(
                        "$.components.schemas.CompleteMaintenanceRequest.properties.equipmentVersion.minimum")
                        .value(0))
                .andExpect(jsonPath(
                        "$.components.schemas.CancelMaintenanceRequest.properties.maintenanceVersion.minimum")
                        .value(0))
                .andExpect(jsonPath(
                        "$.components.schemas.CancelMaintenanceRequest.properties.equipmentVersion.minimum")
                        .value(0));
    }

    @Test
    void documentsRequiredLifecycleRequestFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.UpdateScheduledMaintenanceRequest.required",
                        hasItems("scheduledOn", "currency")))
                .andExpect(jsonPath(
                        "$.components.schemas.StartMaintenanceRequest.required",
                        hasItems("startedAt", "reason")))
                .andExpect(jsonPath(
                        "$.components.schemas.CompleteMaintenanceRequest.required",
                        hasItems(
                                "completedAt",
                                "actionsTaken",
                                "currency",
                                "equipmentOutcome")))
                .andExpect(jsonPath(
                        "$.components.schemas.CancelMaintenanceRequest.required",
                        hasItems("reason")));
    }

    @Test
    void documentsMaintenanceRolesCsrfAndLifecycleSemantics() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances'].post.description",
                        containsString("ADMIN")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances'].post.description",
                        containsString("CSRF")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances/{id}'].put.description",
                        containsString("optimistic locking")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances'].post.description",
                        not(containsString("ROLE_MAINTENANCE"))))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/maintenances'].post.description",
                        not(containsString("MAINTENANCE role"))));
    }
}
