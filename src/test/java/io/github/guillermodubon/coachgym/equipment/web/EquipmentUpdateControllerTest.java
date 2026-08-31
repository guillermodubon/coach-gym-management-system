package io.github.guillermodubon.coachgym.equipment.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentApplicationService;
import io.github.guillermodubon.coachgym.equipment.application.exception.DuplicateSerialNumberException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryInactiveException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryNotFoundException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentNotFoundException;
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
        EquipmentUpdateControllerTest
                .TestSecurityConfiguration.class)
class EquipmentUpdateControllerTest {

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
                    "2025-09-01T08:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EquipmentApplicationService equipmentService;

    private EquipmentDetails updatedDetails() {
        return new EquipmentDetails(
                EQUIPMENT_ID,
                1L,
                "EQP-000001",
                CATEGORY_ID,
                "Cardio",
                "New Name",
                "Acme",
                null,
                null,
                null,
                EquipmentStatus.AVAILABLE,
                null,
                null,
                null,
                null,
                null,
                USER_ID,
                USER_ID,
                NOW,
                NOW,
                1L);
    }

    private String updateBody(
            UUID categoryId,
            String name,
            long version) {

        return """
                {
                  "categoryId": "%s",
                  "name": "%s",
                  "version": %d
                }
                """.formatted(
                categoryId,
                name,
                version);
    }

    private RequestPostProcessor authenticatedAs(
            String role) {

        CoachGymUserPrincipal principal =
                mock(CoachGymUserPrincipal.class);

        when(principal.id())
                .thenReturn(USER_ID);

        when(principal.getUsername())
                .thenReturn(
                        "equipment-update-test-user");

        Authentication userAuthentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_" + role)));

        return authentication(userAuthentication);
    }

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    void update_returns200_withUpdatedDetails()
            throws Exception {

        when(equipmentService.update(
                any(),
                any()))
                .thenReturn(updatedDetails());

        mockMvc.perform(
                        put(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        updateBody(
                                                CATEGORY_ID,
                                                "New Name",
                                                0L)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        EQUIPMENT_ID.toString()))
                .andExpect(
                        jsonPath("$.name")
                                .value("New Name"))
                .andExpect(
                        jsonPath("$.equipmentCode")
                                .value("EQP-000001"))
                .andExpect(
                        jsonPath("$.categoryId")
                                .value(
                                        CATEGORY_ID.toString()))
                .andExpect(
                        jsonPath("$.status")
                                .value("AVAILABLE"))
                .andExpect(
                        jsonPath("$.version")
                                .value(1));
    }

    // -------------------------------------------------------------------------
    // Business error cases
    // -------------------------------------------------------------------------

    @Test
    void update_returns404_whenEquipmentNotFound()
            throws Exception {

        when(equipmentService.update(
                any(),
                any()))
                .thenThrow(
                        new EquipmentNotFoundException(
                                EQUIPMENT_ID));

        mockMvc.perform(
                        put(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        updateBody(
                                                CATEGORY_ID,
                                                "Name",
                                                0L)))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "EQUIPMENT_NOT_FOUND"));
    }

    @Test
    void update_returns404_whenCategoryNotFound()
            throws Exception {

        when(equipmentService.update(
                any(),
                any()))
                .thenThrow(
                        new EquipmentCategoryNotFoundException(
                                CATEGORY_ID));

        mockMvc.perform(
                        put(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        updateBody(
                                                CATEGORY_ID,
                                                "Name",
                                                0L)))
                .andExpect(
                        status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "EQUIPMENT_CATEGORY_NOT_FOUND"));
    }

    @Test
    void update_returns409_whenCategoryInactive()
            throws Exception {

        when(equipmentService.update(
                any(),
                any()))
                .thenThrow(
                        new EquipmentCategoryInactiveException(
                                CATEGORY_ID));

        mockMvc.perform(
                        put(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        updateBody(
                                                CATEGORY_ID,
                                                "Name",
                                                0L)))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "EQUIPMENT_CATEGORY_INACTIVE"));
    }

    @Test
    void update_returns409_whenVersionConflict()
            throws Exception {

        when(equipmentService.update(
                any(),
                any()))
                .thenThrow(
                        new EquipmentVersionConflictException(
                                EQUIPMENT_ID));

        mockMvc.perform(
                        put(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        updateBody(
                                                CATEGORY_ID,
                                                "Name",
                                                0L)))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "EQUIPMENT_VERSION_CONFLICT"));
    }

    @Test
    void update_returns409_whenDuplicateSerial()
            throws Exception {

        when(equipmentService.update(
                any(),
                any()))
                .thenThrow(
                        new DuplicateSerialNumberException(
                                "SN-X"));

        mockMvc.perform(
                        put(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "categoryId": "%s",
                                          "name": "Bike",
                                          "serialNumber": "SN-X",
                                          "version": 0
                                        }
                                        """.formatted(
                                        CATEGORY_ID)))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "DUPLICATE_EQUIPMENT_SERIAL"));
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    @Test
    void update_returns400_whenNameBlank()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "categoryId": "%s",
                                          "name": "  ",
                                          "version": 0
                                        }
                                        """.formatted(
                                        CATEGORY_ID)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void update_returns400_whenCategoryIdNull()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Bike",
                                          "version": 0
                                        }
                                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void update_returns400_whenVersionNull()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "categoryId": "%s",
                                          "name": "Bike"
                                        }
                                        """.formatted(
                                        CATEGORY_ID)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(equipmentService);
    }

    // -------------------------------------------------------------------------
    // Authorization and CSRF
    // -------------------------------------------------------------------------

    @Test
    void update_returns401_unauthenticated()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        updateBody(
                                                CATEGORY_ID,
                                                "Name",
                                                0L)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void update_returns403_forMaintenance()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "MAINTENANCE"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        updateBody(
                                                CATEGORY_ID,
                                                "Name",
                                                0L)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void update_returns403_forReceptionist()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "RECEPTIONIST"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        updateBody(
                                                CATEGORY_ID,
                                                "Name",
                                                0L)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void update_returns403_missingCsrf()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        updateBody(
                                                CATEGORY_ID,
                                                "Name",
                                                0L)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(equipmentService);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain equipmentUpdateSecurityFilterChain(
                HttpSecurity http) throws Exception {

            return http
                    .authorizeHttpRequests(authorize ->
                            authorize
                                    .requestMatchers(
                                            HttpMethod.GET,
                                            "/api/v1/equipment/**")
                                    .hasAnyRole(
                                            "ADMIN",
                                            "MAINTENANCE",
                                            "RECEPTIONIST")
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
