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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentDetails;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentStatus;
import io.github.guillermodubon.coachgym.user.StaffBranchContext;
import io.github.guillermodubon.coachgym.user.StaffScopeDetails;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.github.guillermodubon.coachgym.user.application.ActiveBranchContextApplicationService;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentApplicationService;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSearchPage;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSearchResult;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({StaffBranchAssignmentController.class, StaffBranchContextController.class})
@Import({
        StaffBranchAssignmentProblemHandler.class,
        StaffBranchAssignmentControllerTest.TestSecurityConfiguration.class})
class StaffBranchAssignmentControllerTest {

    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000007701");
    private static final UUID TARGET_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000007702");
    private static final UUID BRANCH_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000007703");
    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000007704");
    private static final UUID ASSIGNMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000007705");
    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StaffBranchAssignmentApplicationService service;

    @MockitoBean
    private ActiveBranchContextApplicationService contextService;

    @Test
    void anonymousRequestsAreRejectedAndMutationsRequireCsrf() throws Exception {
        mockMvc.perform(get("/api/v1/staff/branch-assignments"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/branch-context"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/staff/branch-assignments")
                        .with(authenticatedAs("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/me/branch-context")
                        .with(authenticatedAs("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service, contextService);
    }

    @Test
    void organizationAdminCanListAndCreateWithCsrf() throws Exception {
        when(service.findAssignments(any(), any())).thenReturn(page());
        when(service.assign(any(), any())).thenReturn(activeAssignment());

        mockMvc.perform(get("/api/v1/staff/branch-assignments")
                        .with(authenticatedAs("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].assignment.id").value(ASSIGNMENT_ID.toString()))
                .andExpect(jsonPath("$.items[0].branch.code").value("NORTH"));

        mockMvc.perform(post("/api/v1/staff/branch-assignments")
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + TARGET_ID
                                + "\",\"branchId\":\"" + BRANCH_ID
                                + "\",\"reason\":\"coverage\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignmentId").doesNotExist())
                .andExpect(jsonPath("$.id").value(ASSIGNMENT_ID.toString()));

        verify(service).findAssignments(any(), any());
        verify(service).assign(any(), any());
    }

    @Test
    void receptionistCannotAccessAdministrativeRoutesButCanManageOwnContext() throws Exception {
        when(contextService.resolve(USER_ID)).thenReturn(context());

        mockMvc.perform(get("/api/v1/staff/branch-assignments")
                        .with(authenticatedAs("RECEPTIONIST")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/me/branch-context")
                        .with(authenticatedAs("RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeType").value("BRANCH"))
                .andExpect(jsonPath("$.availableBranches[0].id").value(BRANCH_ID.toString()));
    }

    @Test
    void contextSelectionAndClearRequireCsrfAndReturnSafeProjection() throws Exception {
        when(contextService.select(eq(USER_ID), any())).thenReturn(context());

        mockMvc.perform(put("/api/v1/me/branch-context")
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + BRANCH_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeBranch.id").value(BRANCH_ID.toString()))
                .andExpect(jsonPath("$.activeBranch.address").doesNotExist())
                .andExpect(jsonPath("$.sessionId").doesNotExist());

        mockMvc.perform(delete("/api/v1/me/branch-context")
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(contextService).select(eq(USER_ID), any());
        verify(contextService).clear(USER_ID);
    }

    @Test
    void assignmentEndAndScopeReadChangeUseTheirDedicatedContracts() throws Exception {
        when(service.end(any(), any())).thenReturn(endedAssignment());
        when(service.findScope(eq(TARGET_ID), any())).thenReturn(scope());
        when(service.changeScope(any(), any())).thenReturn(scope());

        mockMvc.perform(post("/api/v1/staff/branch-assignments/" + ASSIGNMENT_ID + "/end")
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"rotation\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ASSIGNMENT_ID.toString()))
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.endReason").value("rotation"));

        mockMvc.perform(get("/api/v1/staff/" + TARGET_ID + "/scope")
                        .with(authenticatedAs("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeType").value("BRANCH"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));

        mockMvc.perform(put("/api/v1/staff/" + TARGET_ID + "/scope")
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedScope\":\"BRANCH\",\"reason\":\"rotation\","
                                + "\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$.version").value(0));

        verify(service).end(any(), any());
        verify(service).findScope(eq(TARGET_ID), any());
        verify(service).changeScope(any(), any());
    }

    @Test
    void staleScopeMapsToStableProblemDetail() throws Exception {
        when(service.changeScope(any(), any()))
                .thenThrow(new io.github.guillermodubon.coachgym.user.application.StaffScopeVersionConflictException(TARGET_ID));

        mockMvc.perform(put("/api/v1/staff/" + TARGET_ID + "/scope")
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedScope\":\"BRANCH\",\"reason\":\"coverage\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAFF_SCOPE_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.detail").value(
                        "The staff scope was modified; reload it and retry."));
    }

    @Test
    void serverControlledFieldsAreRejectedFromAssignmentRequest() throws Exception {
        mockMvc.perform(post("/api/v1/staff/branch-assignments")
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + TARGET_ID
                                + "\",\"branchId\":\"" + BRANCH_ID
                                + "\",\"reason\":\"coverage\",\"status\":\"ENDED\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void invalidAdministrativeFiltersAreRejectedBeforeTheQueryPortRuns() throws Exception {
        mockMvc.perform(get("/api/v1/staff/branch-assignments")
                        .with(authenticatedAs("ADMIN"))
                        .param("sort", "UNSAFE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STAFF_BRANCH_REQUEST_INVALID"));

        mockMvc.perform(get("/api/v1/staff/branch-assignments")
                        .with(authenticatedAs("ADMIN"))
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STAFF_BRANCH_REQUEST_INVALID"));

        verifyNoInteractions(service);
    }

    private static StaffBranchAssignmentSearchPage page() {
        return new StaffBranchAssignmentSearchPage(
                List.of(new StaffBranchAssignmentSearchResult(
                        activeAssignment(), "receptionist", RoleCode.RECEPTIONIST,
                        StaffScopeType.BRANCH, branch())),
                0, 25, 1, 1);
    }

    private static StaffBranchAssignmentDetails activeAssignment() {
        return new StaffBranchAssignmentDetails(
                ASSIGNMENT_ID, TARGET_ID, BRANCH_ID, StaffBranchAssignmentStatus.ACTIVE,
                NOW, USER_ID, null, null, null, 0);
    }

    private static StaffBranchAssignmentDetails endedAssignment() {
        return new StaffBranchAssignmentDetails(
                ASSIGNMENT_ID, TARGET_ID, BRANCH_ID, StaffBranchAssignmentStatus.ENDED,
                NOW.minusSeconds(60), USER_ID, NOW, USER_ID, "rotation", 1);
    }

    private static StaffScopeDetails scope() {
        return new StaffScopeDetails(
                TARGET_ID, Set.of(RoleCode.ADMIN), StaffScopeType.BRANCH,
                NOW, USER_ID, 0);
    }

    private static StaffBranchContext context() {
        return new StaffBranchContext(
                ORGANIZATION_ID, StaffScopeType.BRANCH, BRANCH_ID, List.of(branch()));
    }

    private static AuthorizedBranchSummary branch() {
        return new AuthorizedBranchSummary(
                BRANCH_ID, ORGANIZATION_ID, "NORTH", "North", "America/El_Salvador", true);
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
        SecurityFilterChain staffBranchSecurityFilterChain(HttpSecurity http)
                throws Exception {
            return http
                    .csrf(org.springframework.security.config.Customizer.withDefaults())
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(HttpMethod.GET, "/api/v1/me/branch-context")
                            .hasAnyRole("ADMIN", "RECEPTIONIST")
                            .requestMatchers("/api/v1/me/branch-context")
                            .hasAnyRole("ADMIN", "RECEPTIONIST")
                            .requestMatchers("/api/v1/staff/**")
                            .hasRole("ADMIN")
                            .anyRequest().denyAll())
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint((request, response, exception) ->
                                    response.setStatus(HttpStatus.UNAUTHORIZED.value()))
                            .accessDeniedHandler((request, response, exception) ->
                                    response.setStatus(HttpStatus.FORBIDDEN.value())))
                    .build();
        }
    }
}
