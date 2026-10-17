package io.github.guillermodubon.coachgym.organization.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.organization.GymBranchDetails;
import io.github.guillermodubon.coachgym.organization.GymBranchStatus;
import io.github.guillermodubon.coachgym.organization.application.GymBranchApplicationService;
import io.github.guillermodubon.coachgym.organization.application.GymBranchPage;
import io.github.guillermodubon.coachgym.organization.application.GymBranchSearchQuery;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

@WebMvcTest(GymBranchController.class)
@Import({OrganizationProblemHandler.class, GymBranchControllerTest.TestSecurityConfiguration.class})
class GymBranchControllerTest {

    private static final UUID BRANCH_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZATION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(USER_ID, "branch-admin");
    private static final Instant NOW = Instant.parse("2026-09-20T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GymBranchApplicationService service;

    @Test
    void listIsBoundedAndAllowlistedForBothStaffRoles() throws Exception {
        when(service.findAll(any())).thenReturn(new GymBranchPage(
                List.of(details()), 0, 25, 1, 1));

        mockMvc.perform(get("/api/v1/branches")
                        .with(authenticatedAs("RECEPTIONIST"))
                        .param("status", "active")
                        .param("search", " North ")
                        .param("city", "San Salvador")
                        .param("countryCode", "sv")
                        .param("page", "0")
                        .param("size", "25")
                        .param("sort", "name")
                        .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("NORTH"))
                .andExpect(jsonPath("$.totalElements").value(1));
        verify(service).findAll(any(GymBranchSearchQuery.class));
    }

    @Test
    void createReturnsLocationAndRequiresAdminCsrf() throws Exception {
        when(service.create(any(), eq(ACTOR))).thenReturn(details());
        String body = """
                {
                  "code": "NORTH",
                  "name": "North Gym",
                  "city": "San Salvador",
                  "countryCode": "SV"
                }
                """;

        mockMvc.perform(post("/api/v1/branches")
                        .with(authenticatedAs("RECEPTIONIST"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/branches")
                        .with(authenticatedAs("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/branches")
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.endsWith("/api/v1/branches/" + BRANCH_ID)))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        verify(service).create(any(), eq(ACTOR));
    }

    @Test
    void branchUpdateAndLifecycleRejectUnknownFieldsAndInvalidSort() throws Exception {
        mockMvc.perform(put("/api/v1/branches/" + BRANCH_ID)
                        .with(authenticatedAs("ADMIN"))
                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"North\",\"version\":0,\"status\":\"INACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GYM_BRANCH_VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/branches")
                        .with(authenticatedAs("ADMIN"))
                        .param("sort", "arbitrary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GYM_BRANCH_VALIDATION_FAILED"));
        verify(service, never()).update(any(), any(), any());
    }

    @Test
    void detailIsReadableAndMissingBranchMapsToStableProblem() throws Exception {
        when(service.findById(BRANCH_ID)).thenReturn(details());

        mockMvc.perform(get("/api/v1/branches/" + BRANCH_ID)
                        .with(authenticatedAs("RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NORTH"))
                .andExpect(jsonPath("$.initialBranch").value(true));
        verify(service).findById(BRANCH_ID);
    }

    private GymBranchDetails details() {
        return new GymBranchDetails(
                BRANCH_ID,
                ORGANIZATION_ID,
                "NORTH",
                "North Gym",
                "1 Main Street",
                null,
                "San Salvador",
                null,
                null,
                "SV",
                "+50370000000",
                "north@coach-gym.example",
                "America/El_Salvador",
                GymBranchStatus.ACTIVE,
                true,
                NOW.minusSeconds(3600),
                NOW,
                0);
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
                            .requestMatchers(HttpMethod.POST, "/api/v1/branches").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.PUT, "/api/v1/branches/*").hasRole("ADMIN")
                            .anyRequest().authenticated())
                    .build();
        }
    }
}
