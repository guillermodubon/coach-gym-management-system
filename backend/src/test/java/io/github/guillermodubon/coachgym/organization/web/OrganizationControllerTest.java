package io.github.guillermodubon.coachgym.organization.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.organization.OrganizationDetails;
import io.github.guillermodubon.coachgym.organization.OrganizationStatus;
import io.github.guillermodubon.coachgym.organization.application.OrganizationApplicationService;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.List;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

@WebMvcTest(OrganizationController.class)
@Import({OrganizationProblemHandler.class, OrganizationControllerTest.TestSecurityConfiguration.class})
class OrganizationControllerTest {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(USER_ID, "org-admin");
    private static final Instant NOW = Instant.parse("2026-09-20T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizationApplicationService service;

    @Test
    void adminReceivesAdministrativeOrganizationProjection() throws Exception {
        when(service.findCanonical()).thenReturn(details());

        mockMvc.perform(get("/api/v1/organization").with(authenticatedAs("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COACH_GYM"))
                .andExpect(jsonPath("$.legalName").value("Coach Gym, S.A."))
                .andExpect(jsonPath("$.supportEmail").value("support@coach-gym.example"))
                .andExpect(jsonPath("$.version").value(3));
        verify(service).findCanonical();
    }

    @Test
    void receptionistReceivesSafeSummaryWithoutAdministrativeContactFields() throws Exception {
        when(service.findCanonicalSummary()).thenReturn(
                io.github.guillermodubon.coachgym.organization.OrganizationSummary.from(details()));

        mockMvc.perform(get("/api/v1/organization").with(authenticatedAs("RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COACH_GYM"))
                .andExpect(jsonPath("$.brandName").value("Coach Gym"))
                .andExpect(jsonPath("$.legalName").doesNotExist())
                .andExpect(jsonPath("$.supportEmail").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
        verify(service).findCanonicalSummary();
        verify(service, never()).findCanonical();
    }

    @Test
    void organizationMutationRequiresAdminAndCsrf() throws Exception {
        String body = """
                {
                  "legalName": "Coach Gym, S.A.",
                  "brandName": "Coach Gym",
                  "supportEmail": "support@coach-gym.example",
                  "supportPhone": "+50370000000",
                  "defaultTimezone": "America/El_Salvador",
                  "defaultCurrency": "USD",
                  "version": 3
                }
                """;
        when(service.update(any(), eq(ACTOR))).thenReturn(details());

        mockMvc.perform(put("/api/v1/organization")
                        .with(authenticatedAs("RECEPTIONIST"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/organization")
                        .with(authenticatedAs("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/organization")
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));
        verify(service).update(any(), eq(ACTOR));
    }

    @Test
    void unknownOrganizationRequestFieldIsRejectedWithoutCallingService() throws Exception {
        mockMvc.perform(put("/api/v1/organization")
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"Coach Gym\",\"version\":0,\"organizationId\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_VALIDATION_FAILED"));
        verify(service, never()).update(any(), any());
    }

    private OrganizationDetails details() {
        return new OrganizationDetails(
                ORGANIZATION_ID,
                "COACH_GYM",
                "Coach Gym, S.A.",
                "Coach Gym",
                "support@coach-gym.example",
                "+50370000000",
                "America/El_Salvador",
                "USD",
                OrganizationStatus.ACTIVE,
                NOW.minusSeconds(3600),
                NOW,
                3);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor authenticatedAs(
            String role) {
        CoachGymUserPrincipal principal = org.mockito.Mockito.mock(CoachGymUserPrincipal.class);
        when(principal.authenticatedActor()).thenReturn(ACTOR);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return authentication(authentication);
    }

    @TestConfiguration
    @EnableMethodSecurity
    @EnableWebSecurity
    static class TestSecurityConfiguration {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> { })
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                            .accessDeniedHandler(new AccessDeniedHandlerImpl()))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(HttpMethod.PUT, "/api/v1/organization").hasRole("ADMIN")
                            .anyRequest().authenticated())
                    .build();
        }
    }
}
