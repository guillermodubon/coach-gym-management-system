package io.github.guillermodubon.coachgym.equipment.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentApplicationService;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentNotFoundException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentStateConflictException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentVersionConflictException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(EquipmentController.class)
@Import(
        EquipmentLifecycleControllerTest
                .TestSecurityConfiguration.class)
class EquipmentLifecycleControllerTest {

    private static final UUID EQUIPMENT_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID CATEGORY_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    private static final UUID USER_ID =
            UUID.fromString(
                    "50000000-0000-0000-0000-000000000001");

    private static final Instant NOW =
            Instant.parse(
                    "2025-10-01T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EquipmentApplicationService equipmentService;

    private EquipmentDetails details(
            EquipmentStatus equipmentStatus) {

        boolean retired =
                equipmentStatus == EquipmentStatus.RETIRED;

        return new EquipmentDetails(
                EQUIPMENT_ID,
                1L,
                "EQP-000001",
                CATEGORY_ID,
                "Cardio",
                "Treadmill",
                null,
                null,
                null,
                null,
                equipmentStatus,
                null,
                null,
                retired ? NOW : null,
                retired ? USER_ID : null,
                retired ? "End of life" : null,
                USER_ID,
                USER_ID,
                NOW,
                NOW,
                1L);
    }

    private String transitionBody(
            long version) {

        return """
                {
                  "reason": "Routine inspection",
                  "version": %d
                }
                """.formatted(version);
    }

    private RequestPostProcessor authenticatedAs(
            String role) {

        CoachGymUserPrincipal principal =
                mock(CoachGymUserPrincipal.class);

        when(principal.id())
                .thenReturn(USER_ID);

        when(principal.getUsername())
                .thenReturn(
                        "equipment-lifecycle-test-user");

        Authentication userAuthentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_" + role)));

        return authentication(userAuthentication);
    }


    @Test
    void outOfService_returns200_forAdmin()
            throws Exception {

        when(equipmentService.markOutOfService(
                any(),
                any()))
                .thenReturn(
                        details(
                                EquipmentStatus.OUT_OF_SERVICE));

        mockMvc.perform(
                        post(
                                "/api/v1/equipment/{id}/out-of-service",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        transitionBody(0L)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value(
                                        "OUT_OF_SERVICE"))
                .andExpect(
                        jsonPath("$.version")
                                .value(1));
    }

    @Test
    void outOfService_returns200_forMaintenance()
            throws Exception {

        when(equipmentService.markOutOfService(
                any(),
                any()))
                .thenReturn(
                        details(
                                EquipmentStatus.OUT_OF_SERVICE));

        mockMvc.perform(
                        post(
                                "/api/v1/equipment/{id}/out-of-service",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        transitionBody(0L)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value(
                                        "OUT_OF_SERVICE"));
    }

    @Test
    void outOfService_returns403_forReceptionist()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/equipment/{id}/out-of-service",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "RECEPTIONIST"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        transitionBody(0L)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void outOfService_returns401_unauthenticated()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/equipment/{id}/out-of-service",
                                EQUIPMENT_ID)
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        transitionBody(0L)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void outOfService_returns403_missingCsrf()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/equipment/{id}/out-of-service",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        transitionBody(0L)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void outOfService_returns409_onStateConflict()
            throws Exception {

        when(equipmentService.markOutOfService(
                any(),
                any()))
                .thenThrow(
                        new EquipmentStateConflictException(
                                io.github.guillermodubon.coachgym
                                        .equipment.domain
                                        .EquipmentStatus.OUT_OF_SERVICE,
                                io.github.guillermodubon.coachgym
                                        .equipment.domain
                                        .EquipmentStatus.OUT_OF_SERVICE,
                                "Equipment is already in status "
                                        + "OUT_OF_SERVICE."));

        mockMvc.perform(
                        post(
                                "/api/v1/equipment/{id}/out-of-service",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        transitionBody(0L)))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "EQUIPMENT_STATE_CONFLICT"));
    }

    @Test
    void outOfService_returns409_onVersionConflict()
            throws Exception {

        when(equipmentService.markOutOfService(
                any(),
                any()))
                .thenThrow(
                        new EquipmentVersionConflictException(
                                EQUIPMENT_ID));

        mockMvc.perform(
                        post(
                                "/api/v1/equipment/{id}/out-of-service",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        transitionBody(0L)))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "EQUIPMENT_VERSION_CONFLICT"));
    }

    @Test
    void outOfService_returns400_whenReasonBlank()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/equipment/{id}/out-of-service",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "reason": "  ",
                                          "version": 0
                                        }
                                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void outOfService_returns400_whenVersionNull()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/equipment/{id}/out-of-service",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "reason": "check"
                                        }
                                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(equipmentService);
    }


    @Test
    void available_returns200_forAdmin()
            throws Exception {

        when(equipmentService.markAvailable(
                any(),
                any()))
                .thenReturn(
                        details(
                                EquipmentStatus.AVAILABLE));

        mockMvc.perform(
                        post(
                                "/api/v1/equipment/{id}/available",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        transitionBody(0L)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("AVAILABLE"))
                .andExpect(
                        jsonPath("$.version")
                                .value(1));
    }

    @Test
    void available_returns200_forMaintenance()
            throws Exception {

        when(equipmentService.markAvailable(
                any(),
                any()))
                .thenReturn(
                        details(
                                EquipmentStatus.AVAILABLE));

        mockMvc.perform(
                        post(
                                "/api/v1/equipment/{id}/available",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        transitionBody(0L)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("AVAILABLE"));
    }

    @Test
    void available_returns403_forReceptionist()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/equipment/{id}/available",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "RECEPTIONIST"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        transitionBody(0L)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(equipmentService);
    }


    @Test
    void retire_returns200_forAdmin()
            throws Exception {

        when(equipmentService.retire(
                any(),
                any()))
                .thenReturn(
                        details(
                                EquipmentStatus.RETIRED));

        mockMvc.perform(
                        post(
                                "/api/v1/equipment/{id}/retire",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        transitionBody(0L)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("RETIRED"))
                .andExpect(
                        jsonPath("$.retiredAt")
                                .exists())
                .andExpect(
                        jsonPath("$.retiredByUserId")
                                .value(
                                        USER_ID.toString()))
                .andExpect(
                        jsonPath("$.retirementReason")
                                .value("End of life"))
                .andExpect(
                        jsonPath("$.version")
                                .value(1));
    }

    @Test
    void retire_returns403_forMaintenance()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/equipment/{id}/retire",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "RECEPTIONIST"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        transitionBody(0L)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void retire_returns409_retiredIsTerminal()
            throws Exception {

        when(equipmentService.retire(
                any(),
                any()))
                .thenThrow(
                        new EquipmentStateConflictException(
                                io.github.guillermodubon.coachgym
                                        .equipment.domain
                                        .EquipmentStatus.RETIRED,
                                io.github.guillermodubon.coachgym
                                        .equipment.domain
                                        .EquipmentStatus.RETIRED,
                                "RETIRED is a terminal status."));

        mockMvc.perform(
                        post(
                                "/api/v1/equipment/{id}/retire",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        transitionBody(0L)))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "EQUIPMENT_STATE_CONFLICT"));
    }

    @Test
    void retire_returns404_whenNotFound()
            throws Exception {

        when(equipmentService.retire(
                any(),
                any()))
                .thenThrow(
                        new EquipmentNotFoundException(
                                EQUIPMENT_ID));

        mockMvc.perform(
                        post(
                                "/api/v1/equipment/{id}/retire",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        transitionBody(0L)))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "EQUIPMENT_NOT_FOUND"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain equipmentLifecycleSecurityFilterChain(
                HttpSecurity http) throws Exception {

            return http
                    .authorizeHttpRequests(authorize ->
                            authorize
                                    .requestMatchers(
                                            HttpMethod.POST,
                                            "/api/v1/equipment/*/out-of-service",
                                            "/api/v1/equipment/*/available")
                                    .hasRole("ADMIN")
                                    .requestMatchers(
                                            HttpMethod.POST,
                                            "/api/v1/equipment/*/retire")
                                    .hasRole("ADMIN")
                                    .requestMatchers(
                                            "/api/v1/equipment/**")
                                    .hasRole("ADMIN")
                                    .anyRequest()
                                    .denyAll())
                    .exceptionHandling(exceptions ->
                            exceptions.authenticationEntryPoint(
                                    (
                                            request,
                                            response,
                                            exception) ->
                                            response.sendError(
                                                    401,
                                                    "Authentication required")))
                    .build();
        }
    }
}
