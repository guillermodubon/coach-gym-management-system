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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDetails;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentCategoryApplicationService;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentCategoryPage;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentCategorySearchQuery;
import io.github.guillermodubon.coachgym.equipment.application.exception.DuplicateEquipmentCategoryException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryNotFoundException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryVersionConflictException;
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


@WebMvcTest(EquipmentCategoryController.class)
@Import(
        EquipmentCategoryControllerTest
                .TestSecurityConfiguration.class)
class EquipmentCategoryControllerTest {

    private static final UUID CATEGORY_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID USER_ID =
            UUID.fromString(
                    "50000000-0000-0000-0000-000000000001");

    private static final Instant NOW =
            Instant.parse(
                    "2025-01-15T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EquipmentCategoryApplicationService categoryService;

    private EquipmentCategoryDetails sampleDetails() {
        return new EquipmentCategoryDetails(
                CATEGORY_ID,
                "Cardio",
                "Cardio equipment",
                true,
                NOW,
                NOW,
                0L);
    }

    private RequestPostProcessor authenticatedAs(
            String role) {

        CoachGymUserPrincipal principal =
                mock(CoachGymUserPrincipal.class);

        /*
         * If CoachGymUserPrincipal exposes id() rather than userId(),
         * replace principal.userId() with principal.id().
         */
        when(principal.id())
                .thenReturn(USER_ID);

        when(principal.getUsername())
                .thenReturn("test-user");

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
    void createReturns201WithLocation()
            throws Exception {

        when(categoryService.create(
                any(),
                any()))
                .thenReturn(sampleDetails());

        mockMvc.perform(
                        post(
                                "/api/v1/equipment-categories")
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Cardio",
                                          "description": "Cardio equipment"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                "Location",
                                containsString(
                                        "/api/v1/equipment-categories/"
                                                + CATEGORY_ID)))
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        CATEGORY_ID.toString()))
                .andExpect(
                        jsonPath("$.name")
                                .value("Cardio"))
                .andExpect(
                        jsonPath("$.active")
                                .value(true))
                .andExpect(
                        jsonPath("$.version")
                                .value(0));
    }

    @Test
    void createReturns409OnDuplicate()
            throws Exception {

        when(categoryService.create(
                any(),
                any()))
                .thenThrow(
                        new DuplicateEquipmentCategoryException(
                                "Cardio"));

        mockMvc.perform(
                        post(
                                "/api/v1/equipment-categories")
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Cardio"
                                        }
                                        """))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "DUPLICATE_EQUIPMENT_CATEGORY"));
    }

    @Test
    void createReturns400WhenNameBlank()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/equipment-categories")
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "  "
                                        }
                                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns403MissingCsrf()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/equipment-categories")
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Cardio"
                                        }
                                        """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void createReturns401Unauthenticated()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/equipment-categories")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Cardio"
                                        }
                                        """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(categoryService);
    }

    @Test
    void createReturns403ForMaintenance()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/equipment-categories")
                                .with(
                                        authenticatedAs(
                                                "MAINTENANCE"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Cardio"
                                        }
                                        """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void createReturns403ForReceptionist()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/equipment-categories")
                                .with(
                                        authenticatedAs(
                                                "RECEPTIONIST"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Cardio"
                                        }
                                        """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void findByIdReturns200ForAdmin()
            throws Exception {

        when(categoryService.findById(CATEGORY_ID))
                .thenReturn(sampleDetails());

        mockMvc.perform(
                        get(
                                "/api/v1/equipment-categories/{id}",
                                CATEGORY_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        CATEGORY_ID.toString()))
                .andExpect(
                        jsonPath("$.name")
                                .value("Cardio"));
    }

    @Test
    void findByIdReturns200ForMaintenance()
            throws Exception {

        when(categoryService.findById(CATEGORY_ID))
                .thenReturn(sampleDetails());

        mockMvc.perform(
                        get(
                                "/api/v1/equipment-categories/{id}",
                                CATEGORY_ID)
                                .with(
                                        authenticatedAs(
                                                "MAINTENANCE")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        CATEGORY_ID.toString()));
    }

    @Test
    void findByIdReturns200ForReceptionist()
            throws Exception {

        when(categoryService.findById(CATEGORY_ID))
                .thenReturn(sampleDetails());

        mockMvc.perform(
                        get(
                                "/api/v1/equipment-categories/{id}",
                                CATEGORY_ID)
                                .with(
                                        authenticatedAs(
                                                "RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        CATEGORY_ID.toString()));
    }

    @Test
    void findByIdReturns404WhenNotFound()
            throws Exception {

        when(categoryService.findById(CATEGORY_ID))
                .thenThrow(
                        new EquipmentCategoryNotFoundException(
                                CATEGORY_ID));

        mockMvc.perform(
                        get(
                                "/api/v1/equipment-categories/{id}",
                                CATEGORY_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "EQUIPMENT_CATEGORY_NOT_FOUND"));
    }

    @Test
    void findByIdReturns401Unauthenticated()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/equipment-categories/{id}",
                                CATEGORY_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(categoryService);
    }

    @Test
    void findAllReturns200EmptyPage()
            throws Exception {

        when(categoryService.findAll(
                any(
                        EquipmentCategorySearchQuery.class)))
                .thenReturn(
                        new EquipmentCategoryPage(
                                List.of(),
                                0,
                                25,
                                0L,
                                0));

        mockMvc.perform(
                        get(
                                "/api/v1/equipment-categories")
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
    void findAllReturns400ForInvalidSortField()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/equipment-categories")
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .param(
                                        "sort",
                                        "INVALID"))
                .andExpect(status().isBadRequest());
    }


    @Test
    void updateReturns200()
            throws Exception {

        when(categoryService.update(
                any(),
                any()))
                .thenReturn(sampleDetails());

        mockMvc.perform(
                        put(
                                "/api/v1/equipment-categories/{id}",
                                CATEGORY_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Cardio",
                                          "version": 0
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.name")
                                .value("Cardio"))
                .andExpect(
                        jsonPath("$.version")
                                .value(0));
    }

    @Test
    void updateReturns409OnVersionConflict()
            throws Exception {

        when(categoryService.update(
                any(),
                any()))
                .thenThrow(
                        new EquipmentCategoryVersionConflictException(
                                CATEGORY_ID));

        mockMvc.perform(
                        put(
                                "/api/v1/equipment-categories/{id}",
                                CATEGORY_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Cardio",
                                          "version": 0
                                        }
                                        """))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "EQUIPMENT_CATEGORY_VERSION_CONFLICT"));
    }

    @Test
    void activateReturns200()
            throws Exception {

        when(categoryService.activate(
                any(),
                any()))
                .thenReturn(
                        new EquipmentCategoryDetails(
                                CATEGORY_ID,
                                "Cardio",
                                null,
                                true,
                                NOW,
                                NOW,
                                2L));

        mockMvc.perform(
                        post(
                                "/api/v1/equipment-categories/{id}/activate",
                                CATEGORY_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "version": 1
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.active")
                                .value(true))
                .andExpect(
                        jsonPath("$.version")
                                .value(2));
    }

    @Test
    void deactivateReturns200()
            throws Exception {

        when(categoryService.deactivate(
                any(),
                any()))
                .thenReturn(
                        new EquipmentCategoryDetails(
                                CATEGORY_ID,
                                "Cardio",
                                null,
                                false,
                                NOW,
                                NOW,
                                2L));

        mockMvc.perform(
                        post(
                                "/api/v1/equipment-categories/{id}/deactivate",
                                CATEGORY_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "version": 1
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.active")
                                .value(false))
                .andExpect(
                        jsonPath("$.version")
                                .value(2));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain equipmentCategorySecurityFilterChain(
                HttpSecurity http) throws Exception {

            return http
                    .authorizeHttpRequests(authorize ->
                            authorize
                                    .requestMatchers(
                                            HttpMethod.GET,
                                            "/api/v1/equipment-categories/**")
                                    .hasAnyRole(
                                            "ADMIN",
                                            "MAINTENANCE",
                                            "RECEPTIONIST")
                                    .requestMatchers(
                                            "/api/v1/equipment-categories/**")
                                    .hasRole("ADMIN")
                                    .anyRequest()
                                    .denyAll())
                    .exceptionHandling(exceptions ->
                            exceptions.authenticationEntryPoint(
                                    (request, response, exception) ->
                                            response.sendError(
                                                    401,
                                                    "Authentication required")))
                    .build();
        }
    }

    @Test
    void updateReturns400WhenVersionNull()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/api/v1/equipment-categories/{id}",
                                CATEGORY_ID)
                                .with(
                                        authenticatedAs(
                                                "ADMIN"))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "name": "Cardio"
                                    }
                                    """))
                .andExpect(
                        status().isBadRequest());

        verifyNoInteractions(categoryService);
    }
}
