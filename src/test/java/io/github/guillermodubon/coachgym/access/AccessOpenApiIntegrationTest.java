package io.github.guillermodubon.coachgym.access;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class AccessOpenApiIntegrationTest extends AbstractAccessApiIntegrationTest {

    @Test
    void documentsCheckInContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/access/check-in'].post").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/access/check-in'].post.responses['200'].description",
                        containsString("ALLOWED or DENIED")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/access/check-in'].post.responses['400']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/access/check-in'].post.responses['401']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/access/check-in'].post.responses['403']").exists());
    }

    @Test
    void documentsRecordQueries() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/access/records/{id}'].get.responses['200']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/access/records/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/access/records'].get.responses['200']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/access/records'].get.responses['400']").exists());
    }

    @Test
    void exposesOnlyApprovedCheckInRequestField() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.CheckInRequest.required[0]").value("identifier"))
                .andExpect(jsonPath(
                        "$.components.schemas.CheckInRequest.properties.identifier.maxLength")
                        .value(64))
                .andExpect(jsonPath(
                        "$.components.schemas.CheckInRequest.properties.result")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.CheckInRequest.properties.actor")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.CheckInRequest.properties.clientId")
                        .doesNotExist());
    }
}
