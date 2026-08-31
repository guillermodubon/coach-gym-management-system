package io.github.guillermodubon.coachgym.equipment;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
class EquipmentCategoryApiIntegrationTest extends AbstractEquipmentApiIntegrationTest {

    @BeforeEach
    void provision() {
        jdbcTemplate.update("delete from gym.audit_entries where action_code like 'EQUIPMENT_CATEGORY%'");
        jdbcTemplate.update("delete from gym.equipment_categories");
        provisionUser(ADMIN_USERNAME, "eq-admin@example.com", ADMIN_PASSWORD, "ADMIN");
        provisionUser(MAINTENANCE_USERNAME, "eq-maint@example.com", MAINTENANCE_PASSWORD, "MAINTENANCE");
        provisionUser(RECEPTIONIST_USERNAME, "eq-recept@example.com", RECEPTIONIST_PASSWORD, "RECEPTIONIST");
    }

    @Test
    void create_returns201_andLocationHeader() throws Exception {
        MockHttpSession session = loginAsAdmin();

        MvcResult result = mockMvc.perform(post("/api/v1/equipment-categories")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cardio\",\"description\":\"Treadmills and bikes\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Cardio"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        UUID id = responseId(result);
        assertThat(id).isNotNull();
        assertThat(result.getResponse().getHeader("Location")).contains(id.toString());
    }

    @Test
    void create_returns409_onDuplicateName() throws Exception {
        MockHttpSession session = loginAsAdmin();
        createCategory(session, "Weights");

        mockMvc.perform(post("/api/v1/equipment-categories")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Weights\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EQUIPMENT_CATEGORY"));
    }

    @Test
    void create_returns409_onDuplicateNameCaseInsensitive() throws Exception {
        MockHttpSession session = loginAsAdmin();
        createCategory(session, "weights");

        mockMvc.perform(post("/api/v1/equipment-categories")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WEIGHTS\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void create_returns403_forMaintenance() throws Exception {
        MockHttpSession session = loginAsMaintenance();
        mockMvc.perform(post("/api/v1/equipment-categories")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cardio\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_returns403_missingCsrf() throws Exception {
        MockHttpSession session = loginAsAdmin();
        mockMvc.perform(post("/api/v1/equipment-categories")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cardio\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_returns401_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/equipment-categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cardio\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findById_returns200_forAdmin() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID id = createCategory(session, "Stretching");

        mockMvc.perform(get("/api/v1/equipment-categories/{id}", id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Stretching"));
    }

    @Test
    void findById_returns200_forMaintenance() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        UUID id = createCategory(adminSession, "FreeWeights");

        MockHttpSession mSession = loginAsMaintenance();
        mockMvc.perform(get("/api/v1/equipment-categories/{id}", id).session(mSession))
                .andExpect(status().isOk());
    }

    @Test
    void findById_returns200_forReceptionist() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        UUID id = createCategory(adminSession, "FreeWeights2");

        MockHttpSession rSession = loginAsReceptionist();
        mockMvc.perform(get("/api/v1/equipment-categories/{id}", id).session(rSession))
                .andExpect(status().isOk());
    }

    @Test
    void findById_returns404_whenNotFound() throws Exception {
        MockHttpSession session = loginAsAdmin();
        mockMvc.perform(get("/api/v1/equipment-categories/{id}", UUID.randomUUID()).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_CATEGORY_NOT_FOUND"));
    }

    @Test
    void findAll_returns200_paginatedResult() throws Exception {
        MockHttpSession session = loginAsAdmin();
        createCategory(session, "CatA");
        createCategory(session, "CatB");

        mockMvc.perform(get("/api/v1/equipment-categories")
                        .session(session)
                        .param("sort", "name")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void findAll_filterByActive_returnsOnlyActive() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID inactiveId = createCategory(session, "ToDeactivate");
        deactivateCategory(session, inactiveId, 0L);

        MvcResult result = mockMvc.perform(get("/api/v1/equipment-categories")
                        .session(session)
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(body, "$.items.length()"))
                .isGreaterThanOrEqualTo(0);
    }

    @Test
    void update_returns200_withNewName() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID id = createCategory(session, "OldName");

        mockMvc.perform(put("/api/v1/equipment-categories/{id}", id)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"NewName\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("NewName"));
    }

    @Test
    void update_returns409_onVersionConflict() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID id = createCategory(session, "UpdateMe");

        mockMvc.perform(put("/api/v1/equipment-categories/{id}", id)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"UpdateMe2\",\"version\":99}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_CATEGORY_VERSION_CONFLICT"));
    }

    @Test
    void deactivate_returns200_andActiveFalse() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID id = createCategory(session, "DeactivateMe");

        mockMvc.perform(post("/api/v1/equipment-categories/{id}/deactivate", id)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void activate_returns200_andActiveTrue() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID id = createCategory(session, "ActivateMe");
        deactivateCategory(session, id, 0L);

        mockMvc.perform(post("/api/v1/equipment-categories/{id}/activate", id)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }


    @Test
    void create_persistsAuditEntry() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID id = createCategory(session, "AuditMe");

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries " +
                "where action_code='EQUIPMENT_CATEGORY_CREATED' and resource_id=?",
                Integer.class, id);
        assertThat(count).isEqualTo(1);
    }


    @Test
    void directInsert_blankName_violatesDbConstraint() {
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                jdbcTemplate.update(
                        "insert into gym.equipment_categories (id, name, is_active) values (?,?,true)",
                        UUID.randomUUID(), "  "));
    }

    //helpers
    private UUID createCategory(MockHttpSession session, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/equipment-categories")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private void deactivateCategory(MockHttpSession session, UUID id, long version) throws Exception {
        mockMvc.perform(post("/api/v1/equipment-categories/{id}/deactivate", id)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().isOk());
    }
}
