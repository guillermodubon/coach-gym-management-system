package io.github.guillermodubon.coachgym.accesscredential;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/** OpenAPI contract for the staff access-credential routes. */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
class AccessCredentialOpenApiIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("coach-gym.bootstrap.admin.enabled", () -> false);
    }

    @Test
    void documentsCredentialLifecycleHistoryAndPngRoutes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{clientId}/access-credential'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{clientId}/access-credential'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{clientId}/access-credential/content'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{clientId}/access-credential/revoke'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{clientId}/access-credential/replace'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{clientId}/access-credential/history'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/clients/{clientId}/access-credential/content'].get.responses['200'].content['image/png'].schema.format")
                        .value("binary"))
                .andExpect(jsonPath("$.paths['/api/v1/clients/{clientId}/access-credential'].post.security[0].sessionCookie")
                        .exists());
    }

    @Test
    void documentsOnlyServerControlledCredentialRequestAndSafeResponseFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.IssueAccessCredentialRequest").exists())
                .andExpect(jsonPath("$.components.schemas.IssueAccessCredentialRequest.properties.token").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AccessCredentialResponse.properties.token").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AccessCredentialResponse.properties.tokenFingerprint").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AccessCredentialResponse.properties.storageKey").doesNotExist())
                .andExpect(content().string(not(containsString("rawToken"))));
    }
}
