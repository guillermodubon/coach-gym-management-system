package io.github.guillermodubon.coachgym.accesscredential.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDocument;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialHistoryDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialApplicationService;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialClientNotFoundException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialContent;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialDuplicateException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialHistoryPage;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialNotFoundException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStateConflictException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStorageException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialTokenGenerationException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialValidationException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialVersionConflictException;
import io.github.guillermodubon.coachgym.accesscredential.application.IssueAccessCredentialCommand;
import io.github.guillermodubon.coachgym.accesscredential.application.RevokeClientAccessCredentialCommand;
import io.github.guillermodubon.coachgym.accesscredential.application.ReplaceClientAccessCredentialCommand;
import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AccessCredentialController.class)
@Import({AccessCredentialProblemHandler.class, AccessCredentialControllerTest.TestSecurityConfiguration.class})
class AccessCredentialControllerTest {

    private static final UUID CLIENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-00000000c101");
    private static final UUID CREDENTIAL_ID = UUID.fromString(
            "00000000-0000-0000-0000-00000000c102");
    private static final UUID REPLACEMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-00000000c103");
    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-00000000c104");
    private static final Instant ISSUED_AT = Instant.parse("2026-09-13T10:00:00Z");
    private static final byte[] PNG = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01
    };

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessCredentialApplicationService service;

    @Test
    void issuesSafeMetadataAndRejectsClientControlledFields() throws Exception {
        when(service.issue(eq(new IssueAccessCredentialCommand(CLIENT_ID)), any()))
                .thenReturn(activeDetails());

        mockMvc.perform(post(path())
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(path())))
                .andExpect(jsonPath("$.id").value(CREDENTIAL_ID.toString()))
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.credentialCode").value("AC-C101"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.downloadUrl", containsString("/content")))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.tokenFingerprint").doesNotExist())
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.bytes").doesNotExist());

        mockMvc.perform(post(path())
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"client-controlled\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        verify(service).issue(
                eq(new IssueAccessCredentialCommand(CLIENT_ID)),
                eq(new AuthenticatedActor(USER_ID, "staff-user")));
    }

    @Test
    void returnsMetadataToReceptionistAndDownloadsPrivateNoStorePng() throws Exception {
        when(service.findActiveByClientId(eq(CLIENT_ID), any(AuthenticatedActor.class)))
                .thenReturn(activeDetails());
        when(service.downloadActiveByClientId(eq(CLIENT_ID), any(AuthenticatedActor.class)))
                .thenReturn(new AccessCredentialContent(
                        activeDetails(), new AccessCredentialDocument(MediaType.IMAGE_PNG_VALUE, PNG)));

        mockMvc.perform(get(path()).with(authenticatedAs("RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.downloadUrl", containsString("/content")))
                .andExpect(jsonPath("$.checksumSha256").doesNotExist())
                .andExpect(jsonPath("$.storageKey").doesNotExist());

        mockMvc.perform(get(path() + "/content").with(authenticatedAs("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("Content-Length", String.valueOf(PNG.length)))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"coach-gym-AC-C101.png\""))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void revokesAndReplacesWithReasonAndExpectedVersion() throws Exception {
        AccessCredentialDetails revoked = revokedDetails();
        AccessCredentialDetails replacement = replacementDetails();
        when(service.revoke(any(RevokeClientAccessCredentialCommand.class), any()))
                .thenReturn(revoked);
        when(service.replace(any(ReplaceClientAccessCredentialCommand.class), any()))
                .thenReturn(replacement);

        mockMvc.perform(post(path() + "/revoke")
                        .with(authenticatedAs("RECEPTIONIST"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"lost card\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        mockMvc.perform(post(path() + "/replace")
                        .with(authenticatedAs("RECEPTIONIST"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"lost card\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(REPLACEMENT_ID.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(service).revoke(
                eq(new RevokeClientAccessCredentialCommand(CLIENT_ID, "lost card", 0)), any());
        verify(service).replace(
                eq(new ReplaceClientAccessCredentialCommand(CLIENT_ID, "lost card", 0)), any());
    }

    @Test
    void rejectsServerControlledFieldsOnLifecycleRequests() throws Exception {
        mockMvc.perform(post(path() + "/revoke")
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"lost card\",\"version\":0,\"credentialCode\":\"fake\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(post(path() + "/replace")
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"lost card\",\"version\":0,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        verifyNoInteractions(service);
    }

    @Test
    void returnsNewestFirstHistoryPage() throws Exception {
        AccessCredentialHistoryDetails entry = new AccessCredentialHistoryDetails(
                UUID.fromString("00000000-0000-0000-0000-00000000c105"),
                CREDENTIAL_ID,
                CLIENT_ID,
                null,
                AccessCredentialStatus.ACTIVE,
                null,
                ISSUED_AT,
                USER_ID,
                null);
        when(service.findHistoryByClientId(
                eq(CLIENT_ID), eq(0), eq(25), any(AuthenticatedActor.class)))
                .thenReturn(new AccessCredentialHistoryPage(List.of(entry), 0, 25, 1, 1));

        mockMvc.perform(get(path() + "/history").with(authenticatedAs("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].credentialId").value(CREDENTIAL_ID.toString()))
                .andExpect(jsonPath("$.content[0].token").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void enforcesAuthenticationCsrfAndRoleMatrix() throws Exception {
        mockMvc.perform(get(path())).andExpect(status().isUnauthorized());
        mockMvc.perform(post(path())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(path())
                        .with(authenticatedAs("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path())
                        .with(authenticatedAs("MAINTENANCE"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void mapsFailuresToStablePrivacySafeProblemDetails() throws Exception {
        when(service.findActiveByClientId(eq(CLIENT_ID), any(AuthenticatedActor.class)))
                .thenThrow(new AccessCredentialNotFoundException(CLIENT_ID));
        mockMvc.perform(get(path()).with(authenticatedAs("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCESS_CREDENTIAL_NOT_FOUND"));

        reset(service);
        doThrow(new AccessCredentialClientNotFoundException(CLIENT_ID))
                .when(service).issue(any(), any());
        mockMvc.perform(post(path()).with(authenticatedAs("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));

        reset(service);
        doThrow(new AccessCredentialValidationException("internal detail"))
                .when(service).issue(any(), any());
        mockMvc.perform(post(path()).with(authenticatedAs("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCESS_CREDENTIAL_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("The access credential request is invalid."));

        reset(service);
        doThrow(new AccessCredentialVersionConflictException(CREDENTIAL_ID, 0, 1))
                .when(service).revoke(any(RevokeClientAccessCredentialCommand.class), any());
        mockMvc.perform(post(path() + "/revoke").with(authenticatedAs("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"lost card\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCESS_CREDENTIAL_VERSION_CONFLICT"));

        reset(service);
        doThrow(new CannotAcquireLockException("lock busy"))
                .when(service).revoke(any(RevokeClientAccessCredentialCommand.class), any());
        mockMvc.perform(post(path() + "/revoke").with(authenticatedAs("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"lost card\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCESS_CREDENTIAL_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.detail")
                        .value("The access credential was changed by another request."));

        reset(service);
        doThrow(new AccessCredentialTokenGenerationException("internal token", null))
                .when(service).issue(any(), any());
        mockMvc.perform(post(path()).with(authenticatedAs("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ACCESS_CREDENTIAL_TOKEN_GENERATION_FAILED"));
    }

    @Test
    void mapsRemainingStorageAndStateFailuresWithoutLeakingDetails() throws Exception {
        when(service.downloadActiveByClientId(eq(CLIENT_ID), any(AuthenticatedActor.class)))
                .thenThrow(new AccessCredentialStorageException("C:\\secret\\path"));
        mockMvc.perform(get(path() + "/content").with(authenticatedAs("ADMIN")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ACCESS_CREDENTIAL_STORAGE_FAILED"))
                .andExpect(jsonPath("$.detail").value(
                        "The access credential document is temporarily unavailable."));

        reset(service);
        doThrow(new AccessCredentialDuplicateException(
                AccessCredentialDuplicateException.Kind.ACTIVE_CLIENT))
                .when(service).issue(any(), any());
        mockMvc.perform(post(path()).with(authenticatedAs("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCESS_CREDENTIAL_ALREADY_ACTIVE"));

        reset(service);
        doThrow(new AccessCredentialStateConflictException(
                CREDENTIAL_ID, AccessCredentialStatus.REVOKED, AccessCredentialStatus.REVOKED))
                .when(service).revoke(any(RevokeClientAccessCredentialCommand.class), any());
        mockMvc.perform(post(path() + "/revoke").with(authenticatedAs("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"lost card\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCESS_CREDENTIAL_STATE_CONFLICT"));
    }

    private static String path() {
        return "/api/v1/clients/" + CLIENT_ID + "/access-credential";
    }

    private static AccessCredentialDetails activeDetails() {
        return new AccessCredentialDetails(
                CREDENTIAL_ID, CLIENT_ID, "AC-C101", AccessCredentialStatus.ACTIVE,
                "v1", ISSUED_AT, USER_ID, null, null, null, 0);
    }

    private static AccessCredentialDetails revokedDetails() {
        return new AccessCredentialDetails(
                CREDENTIAL_ID, CLIENT_ID, "AC-C101", AccessCredentialStatus.REVOKED,
                "v1", ISSUED_AT, USER_ID, ISSUED_AT.plusSeconds(1), USER_ID, null, 1);
    }

    private static AccessCredentialDetails replacementDetails() {
        return new AccessCredentialDetails(
                REPLACEMENT_ID, CLIENT_ID, "AC-C103", AccessCredentialStatus.ACTIVE,
                "v1", ISSUED_AT.plusSeconds(1), USER_ID, null, null, null, 0);
    }

    private static RequestPostProcessor authenticatedAs(String role) {
        CoachGymUserPrincipal principal = org.mockito.Mockito.mock(CoachGymUserPrincipal.class);
        when(principal.authenticatedActor())
                .thenReturn(new AuthenticatedActor(USER_ID, "staff-user"));
        Authentication authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return authentication(authentication);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain accessCredentialSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(HttpMethod.GET, "/api/v1/clients/**")
                            .hasAnyRole("ADMIN", "RECEPTIONIST")
                            .requestMatchers("/api/v1/clients/**")
                            .hasAnyRole("ADMIN", "RECEPTIONIST")
                            .anyRequest().denyAll())
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint((request, response, exception) ->
                                    response.setStatus(401)))
                    .build();
        }
    }
}
