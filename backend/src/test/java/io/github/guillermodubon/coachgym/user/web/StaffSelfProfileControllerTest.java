package io.github.guillermodubon.coachgym.user.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffProfilePhotoDetails;
import io.github.guillermodubon.coachgym.user.StaffSelfProfileDetails;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoContent;
import io.github.guillermodubon.coachgym.user.application.StaffSelfProfileApplicationService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.beans.factory.annotation.Autowired;

@WebMvcTest(StaffSelfProfileController.class)
@Import({StaffSelfProfileProblemHandler.class, StaffSelfProfileControllerTest.TestSecurityConfiguration.class})
class StaffSelfProfileControllerTest {

    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000007401");
    private static final byte[] PNG = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01
    };

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StaffSelfProfileApplicationService service;

    @Test
    void anonymousRequestsAreRejectedAndMutationsRequireCsrf() throws Exception {
        mockMvc.perform(get(path())).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path() + "/photo")).andExpect(status().isUnauthorized());
        mockMvc.perform(put(path()).with(authenticatedAs("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path() + "/password").with(authenticatedAs("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"x\",\"newPassword\":\"x\",\"newPasswordConfirmation\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(path() + "/photo").with(authenticatedAs("ADMIN"))
                        .param("version", "0"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void bothSupportedRolesCanReadAndUpdateOnlyTheirActorDerivedProfile() throws Exception {
        when(service.findProfile(any())).thenReturn(profile(false));
        when(service.update(any(), any())).thenReturn(profile(false));

        for (String role : List.of("ADMIN", "RECEPTIONIST")) {
            mockMvc.perform(get(path()).with(authenticatedAs(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.roles[0]")
                            .value(org.hamcrest.Matchers.anyOf(
                                    org.hamcrest.Matchers.equalTo("ADMIN"),
                                    org.hamcrest.Matchers.equalTo("RECEPTIONIST"))))
                    .andExpect(jsonPath("$.photoPresent").value(false))
                    .andExpect(jsonPath("$.photoUrl").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.organizationScope").doesNotExist());

            mockMvc.perform(put(path()).with(authenticatedAs(role)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"firstName\":\"Ana\",\"lastName\":\"Martinez\",\"version\":0}"))
                    .andExpect(status().isOk());
        }

        verify(service, org.mockito.Mockito.atLeast(2)).findProfile(any());
        verify(service, org.mockito.Mockito.atLeast(2)).update(any(), eq(
                new io.github.guillermodubon.coachgym.user.application.UpdateStaffSelfProfileCommand(
                        "Ana", "Martinez", 0)));
    }

    @Test
    void unsupportedRoleCannotUseSelfProfile() throws Exception {
        mockMvc.perform(get(path()).with(authenticatedAs("MAINTENANCE")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsServerControlledFieldsInsteadOfBindingThem() throws Exception {
        mockMvc.perform(put(path()).with(authenticatedAs("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ana\",\"lastName\":\"Martinez\","
                                + "\"version\":0,\"role\":\"RECEPTIONIST\","
                                + "\"status\":\"DISABLED\",\"organizationScope\":\"all\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void photoDownloadUsesValidatedPrivateNoStoreHeaders() throws Exception {
        when(service.downloadPhoto(any())).thenReturn(
                new StaffProfilePhotoContent(MediaType.IMAGE_PNG_VALUE, PNG));

        mockMvc.perform(get(path() + "/photo").with(authenticatedAs("RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("Content-Length", String.valueOf(PNG.length)))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Content-Disposition",
                        "inline; filename=\"profile-photo.png\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void photoUploadAndRemovalRequireCsrfAndNeverAcceptTargetIds() throws Exception {
        when(service.uploadPhoto(any(), any())).thenReturn(photoDetails());
        when(service.removePhoto(any(), eq(0L))).thenReturn(profile(false));

        MockMultipartHttpServletRequestBuilder upload = multipart(path() + "/photo")
                .file(new MockMultipartFile("file", "profile.png", MediaType.IMAGE_PNG_VALUE, PNG))
                .param("version", "0")
                .with(authenticatedAs("ADMIN"))
                .with(csrf());
        upload.with(request -> {
            request.setMethod(HttpMethod.PUT.name());
            return request;
        });
        mockMvc.perform(upload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value(MediaType.IMAGE_PNG_VALUE));

        mockMvc.perform(delete(path() + "/photo").with(authenticatedAs("ADMIN"))
                        .with(csrf()).param("version", "0")
                        .param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()));
    }

    @Test
    void invalidPhotoContentMapsToSafeProblemDetail() throws Exception {
        MockMultipartHttpServletRequestBuilder upload = multipart(path() + "/photo")
                .file(new MockMultipartFile("file", "profile.txt", MediaType.TEXT_PLAIN_VALUE,
                        "not an image".getBytes()))
                .param("version", "0")
                .with(authenticatedAs("ADMIN"))
                .with(csrf());
        upload.with(request -> {
            request.setMethod(HttpMethod.PUT.name());
            return request;
        });

        mockMvc.perform(upload)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STAFF_PROFILE_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("The staff profile request is invalid."));
        verifyNoInteractions(service);
    }

    private static String path() {
        return "/api/v1/me/profile";
    }

    private static StaffSelfProfileDetails profile(boolean withPhoto) {
        return new StaffSelfProfileDetails(
                USER_ID, "staff-user", "staff@example.test", "Ana", "Martinez",
                Set.of(RoleCode.ADMIN), StaffAccountStatus.ACTIVE,
                withPhoto ? photoDetails() : null, 0);
    }

    private static StaffProfilePhotoDetails photoDetails() {
        return new StaffProfilePhotoDetails(
                UUID.fromString("00000000-0000-0000-0000-000000007402"),
                MediaType.IMAGE_PNG_VALUE, PNG.length,
                Instant.parse("2026-09-19T12:00:00Z"), 1);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor authenticatedAs(
            String role) {
        CoachGymUserPrincipal principal = org.mockito.Mockito.mock(CoachGymUserPrincipal.class);
        when(principal.authenticatedActor()).thenReturn(
                new AuthenticatedActor(USER_ID, "staff-user"));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return authentication(authentication);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain staffSelfProfileSecurityFilterChain(HttpSecurity http)
                throws Exception {
            return http
                    .csrf(org.springframework.security.config.Customizer.withDefaults())
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(HttpMethod.GET, "/api/v1/me/profile/**")
                            .hasAnyRole("ADMIN", "RECEPTIONIST")
                            .requestMatchers("/api/v1/me/profile/**")
                            .hasAnyRole("ADMIN", "RECEPTIONIST")
                            .anyRequest().denyAll())
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint((request, response, exception) ->
                                    response.setStatus(HttpStatus.UNAUTHORIZED.value())))
                    .build();
        }
    }
}
