package io.github.guillermodubon.coachgym.equipment.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentApplicationService;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentPage;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentSearchQuery;
import io.github.guillermodubon.coachgym.equipment.application.exception.DuplicateSerialNumberException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryInactiveException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryNotFoundException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentNotFoundException;
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
        EquipmentControllerTest
                .TestSecurityConfiguration.class)
class EquipmentControllerTest {

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
                    "2025-06-01T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EquipmentApplicationService equipmentService;

    private EquipmentDetails sampleDetails() {
        return new EquipmentDetails(
                EQUIPMENT_ID,
                1L,
                "EQP-000001",
                CATEGORY_ID,
                "Cardio",
                "Treadmill",
                "LifeFitness",
                "T5",
                null,
                "Room A",
                EquipmentStatus.AVAILABLE,
                null,
                null,
                null,
                null,
                null,
                USER_ID,
                null,
                NOW,
                NOW,
                0L);
    }

    private String registerBody(
            UUID categoryId) {

        return """
                {
                  "categoryId": "%s",
                  "name": "Treadmill"
                }
                """.formatted(categoryId);
    }

    private RequestPostProcessor authenticatedAs(
            String role) {

        CoachGymUserPrincipal principal =
                mock(CoachGymUserPrincipal.class);

        when(principal.id())
                .thenReturn(USER_ID);

        when(principal.getUsername())
                .thenReturn("equipment-test-user");

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
    void register_returns201_withLocationAndEquipmentCode()
            throws Exception {

        when(equipmentService.register(
                any(),
                any()))
                .thenReturn(sampleDetails());

        mockMvc.perform(
                        post("/api/v1/equipment")
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        registerBody(
                                                CATEGORY_ID)))
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                "Location",
                                containsString(
                                        "/api/v1/equipment/"
                                                + EQUIPMENT_ID)))
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        EQUIPMENT_ID.toString()))
                .andExpect(
                        jsonPath("$.equipmentCode")
                                .value("EQP-000001"))
                .andExpect(
                        jsonPath("$.status")
                                .value("AVAILABLE"))
                .andExpect(
                        jsonPath("$.version")
                                .value(0));
    }

    @Test
    void register_returns404_whenCategoryNotFound()
            throws Exception {

        when(equipmentService.register(
                any(),
                any()))
                .thenThrow(
                        new EquipmentCategoryNotFoundException(
                                CATEGORY_ID));

        mockMvc.perform(
                        post("/api/v1/equipment")
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        registerBody(
                                                CATEGORY_ID)))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "EQUIPMENT_CATEGORY_NOT_FOUND"));
    }

    @Test
    void register_returns409_whenCategoryInactive()
            throws Exception {

        when(equipmentService.register(
                any(),
                any()))
                .thenThrow(
                        new EquipmentCategoryInactiveException(
                                CATEGORY_ID));

        mockMvc.perform(
                        post("/api/v1/equipment")
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        registerBody(
                                                CATEGORY_ID)))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "EQUIPMENT_CATEGORY_INACTIVE"));
    }

    @Test
    void register_returns409_whenDuplicateSerial()
            throws Exception {

        when(equipmentService.register(
                any(),
                any()))
                .thenThrow(
                        new DuplicateSerialNumberException(
                                "SN-001"));

        mockMvc.perform(
                        post("/api/v1/equipment")
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
                                          "serialNumber": "SN-001"
                                        }
                                        """.formatted(
                                        CATEGORY_ID)))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "DUPLICATE_EQUIPMENT_SERIAL"));
    }

    @Test
    void register_returns400_whenNameBlank()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/equipment")
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "categoryId": "%s",
                                          "name": "  "
                                        }
                                        """.formatted(
                                        CATEGORY_ID)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void register_returns400_whenCategoryIdNull()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/equipment")
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Treadmill"
                                        }
                                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void register_returns403_missingCsrf()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/equipment")
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        registerBody(
                                                CATEGORY_ID)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void register_returns401_unauthenticated()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/equipment")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        registerBody(
                                                CATEGORY_ID)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void register_returns403_forMaintenance()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/equipment")
                                .with(
                                        authenticatedAs(
                                                "RECEPTIONIST"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        registerBody(
                                                CATEGORY_ID)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void register_returns403_forReceptionist()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/equipment")
                                .with(
                                        authenticatedAs(
                                                "RECEPTIONIST"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        registerBody(
                                                CATEGORY_ID)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(equipmentService);
    }

    @Test
    void findById_returns200_withEquipmentDetails()
            throws Exception {

        when(equipmentService.findById(
                EQUIPMENT_ID))
                .thenReturn(sampleDetails());

        mockMvc.perform(
                        get(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        EQUIPMENT_ID.toString()))
                .andExpect(
                        jsonPath("$.equipmentCode")
                                .value("EQP-000001"))
                .andExpect(
                        jsonPath("$.categoryName")
                                .value("Cardio"))
                .andExpect(
                        jsonPath("$.status")
                                .value("AVAILABLE"));
    }

    @Test
    void findById_returns200_forMaintenance()
            throws Exception {

        when(equipmentService.findById(
                EQUIPMENT_ID))
                .thenReturn(sampleDetails());

        mockMvc.perform(
                        get(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        EQUIPMENT_ID.toString()));
    }

    @Test
    void findById_returns200_forReceptionist()
            throws Exception {

        when(equipmentService.findById(
                EQUIPMENT_ID))
                .thenReturn(sampleDetails());

        mockMvc.perform(
                        get(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        EQUIPMENT_ID.toString()));
    }

    @Test
    void findById_returns404_whenNotFound()
            throws Exception {

        when(equipmentService.findById(
                EQUIPMENT_ID))
                .thenThrow(
                        new EquipmentNotFoundException(
                                EQUIPMENT_ID));

        mockMvc.perform(
                        get(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "EQUIPMENT_NOT_FOUND"));
    }

    @Test
    void findById_returns401_unauthenticated()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/equipment/{id}",
                                EQUIPMENT_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(equipmentService);
    }

    // Find all
    @Test
    void findAll_returns200_emptyPage()
            throws Exception {

        when(equipmentService.findAll(
                any(EquipmentSearchQuery.class)))
                .thenReturn(
                        new EquipmentPage(
                                List.of(),
                                0,
                                25,
                                0L,
                                0));

        mockMvc.perform(
                        get("/api/v1/equipment")
                                .with(
                                        authenticatedAs(
                                                "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items")
                                .isArray())
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(0));
    }

    @Test
    void findAll_returns400_invalidStatus()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/equipment")
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .param(
                                        "status",
                                        "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findAll_returns400_invalidSortField()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/equipment")
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .param(
                                        "sort",
                                        "INVALID_FIELD"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findAll_returns400_negativePage()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/equipment")
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .param(
                                        "page",
                                        "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findAll_returns400_sizeExceedMax()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/equipment")
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .param(
                                        "size",
                                        "101"))
                .andExpect(status().isBadRequest());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain equipmentSecurityFilterChain(
                HttpSecurity http) throws Exception {

            return http
                    .authorizeHttpRequests(authorize ->
                            authorize
                                    .requestMatchers(
                                            HttpMethod.GET,
                                            "/api/v1/equipment/**")
                                    .hasAnyRole(
                                            "ADMIN",
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
