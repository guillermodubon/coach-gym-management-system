package io.github.guillermodubon.coachgym.equipment;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class EquipmentOpenApiIntegrationTest
        extends AbstractEquipmentApiIntegrationTest {

    @Test
    void documentsAllEquipmentPaths()
            throws Exception {

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].get")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].post")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}'].get")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}'].put")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}/out-of-service'].post")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}/available'].post")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}/retire'].post")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment-categories'].get")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment-categories'].post")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment-categories/{id}'].get")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment-categories/{id}'].put")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment-categories/{id}/activate'].post")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment-categories/{id}/deactivate'].post")
                                .exists());
    }

    @Test
    void documentsEquipmentResponseCodes()
            throws Exception {

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())

                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].post.responses['201']")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].post.responses['400']")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].post.responses['401']")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].post.responses['403']")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].post.responses['404']")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].post.responses['409']")
                                .exists())

                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}'].put.responses['200']")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}'].put.responses['400']")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}'].put.responses['401']")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}'].put.responses['403']")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}'].put.responses['404']")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}'].put.responses['409']")
                                .exists());
    }

    @Test
    void documentsLifecycleResponseCodes()
            throws Exception {

        String[] paths = {
                "/api/v1/equipment/{id}/out-of-service",
                "/api/v1/equipment/{id}/available",
                "/api/v1/equipment/{id}/retire"
        };

        for (String path : paths) {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(
                            jsonPath(
                                    "$.paths['"
                                            + path
                                            + "'].post.responses['200']")
                                    .exists())
                    .andExpect(
                            jsonPath(
                                    "$.paths['"
                                            + path
                                            + "'].post.responses['400']")
                                    .exists())
                    .andExpect(
                            jsonPath(
                                    "$.paths['"
                                            + path
                                            + "'].post.responses['401']")
                                    .exists())
                    .andExpect(
                            jsonPath(
                                    "$.paths['"
                                            + path
                                            + "'].post.responses['403']")
                                    .exists())
                    .andExpect(
                            jsonPath(
                                    "$.paths['"
                                            + path
                                            + "'].post.responses['404']")
                                    .exists())
                    .andExpect(
                            jsonPath(
                                    "$.paths['"
                                            + path
                                            + "'].post.responses['409']")
                                    .exists());
        }
    }

    @Test
    void documentsPaginationFiltersAndSorts()
            throws Exception {

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].get.parameters[?(@.name == 'categoryId')]")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].get.parameters[?(@.name == 'status')]")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].get.parameters[?(@.name == 'search')]")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].get.parameters[?(@.name == 'location')]")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].get.parameters[?(@.name == 'page')]")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].get.parameters[?(@.name == 'size')]")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].get.parameters[?(@.name == 'sort')]")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].get.parameters[?(@.name == 'direction')]")
                                .exists());
    }

    @Test
    void documentsRolesCsrfAndOptimisticLocking()
            throws Exception {

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}/out-of-service'].post.description",
                                containsString("ADMIN")))
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}/out-of-service'].post.description",
                                containsString("MAINTENANCE")))
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}/retire'].post.description",
                                containsString("ADMIN")))
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}'].put.description",
                                containsString("optimistic locking")))
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}'].put.description",
                                containsString("CSRF")))
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}/retire'].post.description",
                                containsString("terminal")));
    }

    @Test
    void lifecycleRequestDocumentsConstraints()
            throws Exception {

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.StatusTransitionRequest.required")
                                .isArray())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.StatusTransitionRequest.properties.reason.maxLength")
                                .value(2000))
                .andExpect(
                        jsonPath(
                                "$.components.schemas.StatusTransitionRequest.properties.version.minimum")
                                .value(0));
    }

    @Test
    void equipmentRequestsExcludeServerManagedFields()
            throws Exception {

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())

                .andExpect(
                        jsonPath(
                                "$.components.schemas.RegisterEquipmentRequest.properties.id")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.RegisterEquipmentRequest.properties.equipmentNumber")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.RegisterEquipmentRequest.properties.equipmentCode")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.RegisterEquipmentRequest.properties.status")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.RegisterEquipmentRequest.properties.version")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.UpdateEquipmentRequest.properties.id")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.UpdateEquipmentRequest.properties.equipmentCode")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.UpdateEquipmentRequest.properties.status")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.UpdateEquipmentRequest.properties.retiredAt")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.UpdateEquipmentRequest.properties.retiredByUserId")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.UpdateEquipmentRequest.properties.retirementReason")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.UpdateEquipmentRequest.properties.createdAt")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.UpdateEquipmentRequest.properties.updatedAt")
                                .doesNotExist());
    }

    @Test
    void categoryRequestsSeparateCreateAndUpdateContracts()
            throws Exception {

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.CreateEquipmentCategoryRequest.properties.version")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.UpdateEquipmentCategoryRequest.properties.version")
                                .exists())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.UpdateEquipmentCategoryRequest.properties.version.minimum")
                                .value(0))
                .andExpect(
                        jsonPath(
                                "$.components.schemas.UpdateEquipmentCategoryRequest.properties.id")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.components.schemas.UpdateEquipmentCategoryRequest.properties.active")
                                .doesNotExist());
    }

    @Test
    void equipmentApiExposesNoPhysicalDeleteOperation()
            throws Exception {

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment'].delete")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment/{id}'].delete")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment-categories'].delete")
                                .doesNotExist())
                .andExpect(
                        jsonPath(
                                "$.paths['/api/v1/equipment-categories/{id}'].delete")
                                .doesNotExist());
    }
}
