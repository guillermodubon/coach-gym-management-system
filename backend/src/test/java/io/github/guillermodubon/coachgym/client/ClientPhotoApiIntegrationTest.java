package io.github.guillermodubon.coachgym.client;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientPhotoApiIntegrationTest
        extends AbstractClientProfileApiIntegrationTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Test
    void currentRolesCanUploadAndReadPhotoWhileOnlyAdminCanDelete()
            throws Exception {
        var clientId = insertProfileClient(
                "Photo",
                "Client",
                "photo.client@example.com",
                "+50370008017");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "profile.png",
                "image/png",
                PNG);

        mockMvc.perform(multipart("/api/v1/clients/{id}/photo", clientId)
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .session(loginAsReceptionist())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoId").isNotEmpty())
                .andExpect(jsonPath("$.contentType")
                        .value("image/png"))
                .andExpect(jsonPath("$.sizeBytes")
                        .value(PNG.length))
                .andExpect(jsonPath("$.version")
                        .value(0));

        mockMvc.perform(get("/api/v1/clients/{id}/photo", clientId)
                        .session(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("Cache-Control",
                        containsString("private")))
                .andExpect(header().exists("ETag"));

        long version = jdbcTemplate.queryForObject(
                "select version from gym.client_photos where client_id = ?",
                Long.class,
                clientId);

        mockMvc.perform(delete("/api/v1/clients/{id}/photo", clientId)
                        .session(loginAsReceptionist())
                        .with(csrf())
                        .param("version", Long.toString(version)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/clients/{id}/photo", clientId)
                        .session(loginAsAdmin())
                        .with(csrf())
                        .param("version", Long.toString(version)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/clients/{id}/photo", clientId)
                        .session(loginAsAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnsupportedPhotoTypeAndMissingCsrf() throws Exception {
        var clientId = insertProfileClient(
                "Invalid",
                "Photo",
                "invalid.photo@example.com",
                "+50370008018");
        MockMultipartFile text = new MockMultipartFile(
                "file",
                "profile.txt",
                "text/plain",
                "not-an-image".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/clients/{id}/photo", clientId)
                        .file(text)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .session(loginAsAdmin())
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        MockMultipartFile png = new MockMultipartFile(
                "file", "profile.png", "image/png", PNG);
        mockMvc.perform(multipart("/api/v1/clients/{id}/photo", clientId)
                        .file(png)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .session(loginAsAdmin()))
                .andExpect(status().isForbidden());
    }
}
