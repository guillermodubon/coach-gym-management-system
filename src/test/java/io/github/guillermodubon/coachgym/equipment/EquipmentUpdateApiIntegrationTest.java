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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
class EquipmentUpdateApiIntegrationTest extends AbstractEquipmentApiIntegrationTest {

    private UUID activeCatId;
    private UUID activeCat2Id;
    private UUID inactiveCatId;

    @BeforeEach
    void provision() throws Exception {
        jdbcTemplate.update("delete from gym.audit_entries where action_code like 'EQUIPMENT%'");
        jdbcTemplate.update("delete from gym.equipment");
        jdbcTemplate.update("delete from gym.equipment_categories");
        provisionUser(ADMIN_USERNAME, "eq-admin@example.com", ADMIN_PASSWORD, "ADMIN");
        provisionUser(RECEPTIONIST_USERNAME, "eq-recept@example.com", RECEPTIONIST_PASSWORD, "RECEPTIONIST");

        MockHttpSession admin = loginAsAdmin();
        activeCatId   = createCategory(admin, "Cardio");
        activeCat2Id  = createCategory(admin, "Weights");
        inactiveCatId = createCategory(admin, "OldCat");
        deactivateCategory(admin, inactiveCatId, 0L);
    }

    @Test
    void update_returns200_andFieldsAreChanged() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, activeCatId, "Treadmill", null);

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(activeCatId, "Updated Treadmill", "Acme", "T2", "SN-001", "Room A", 0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Treadmill"))
                .andExpect(jsonPath("$.manufacturer").value("Acme"))
                .andExpect(jsonPath("$.model").value("T2"))
                .andExpect(jsonPath("$.serialNumber").value("SN-001"))
                .andExpect(jsonPath("$.location").value("Room A"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void update_equipmentCodeIsUnchanged() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, activeCatId, "Bike", null);
        String originalCode = equipmentCode(id);

        MvcResult result = mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(activeCatId, "Updated Bike", null, null, null, null, 0L)))
                .andExpect(status().isOk())
                .andReturn();

        String updatedCode = JsonPath.read(result.getResponse().getContentAsString(), "$.equipmentCode");
        assertThat(updatedCode).isEqualTo(originalCode);
    }

    @Test
    void update_statusIsUnchanged() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, activeCatId, "Rower", null);

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(activeCatId, "Updated Rower", null, null, null, null, 0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void update_versionIncrements() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, activeCatId, "Stepper", null);

        MvcResult result = mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(activeCatId, "Stepper v2", null, null, null, null, 0L)))
                .andExpect(status().isOk())
                .andReturn();

        long version = ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.version")).longValue();
        assertThat(version).isEqualTo(1L);
    }

    @Test
    void update_categoryReassignment_toActiveCategory() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, activeCatId, "Barbell", null);

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(activeCat2Id, "Barbell", null, null, null, null, 0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Weights"));
    }

    @Test
    void update_optionalFieldsNullify_whenBlankSent() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, activeCatId, "Elliptical", null);
        // First set a manufacturer
        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(activeCatId, "Elliptical", "Acme", null, null, null, 0L)))
                .andExpect(status().isOk());
        // Then clear it
        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(activeCatId, "Elliptical", "  ", null, null, null, 1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manufacturer").doesNotExist());
    }

    // ── version conflict ──────────────────────────────────────────────────────

    @Test
    void update_returns409_onStaleVersion() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, activeCatId, "VConflict", null);

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(activeCatId, "VConflict Updated", null, null, null, null, 99L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_VERSION_CONFLICT"));
    }

    // ── category failures ─────────────────────────────────────────────────────

    @Test
    void update_returns404_whenCategoryNotFound() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, activeCatId, "CatGone", null);

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(UUID.randomUUID(), "CatGone", null, null, null, null, 0L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_CATEGORY_NOT_FOUND"));
    }

    @Test
    void update_returns409_whenCategoryInactive() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, activeCatId, "InactCat", null);

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(inactiveCatId, "InactCat", null, null, null, null, 0L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_CATEGORY_INACTIVE"));
    }

    // ── serial number ─────────────────────────────────────────────────────────

    @Test
    void update_returns409_whenSerialTakenByOtherEquipment() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        registerEquipment(admin, activeCatId, "Bike A", "SN-SHARED");
        UUID id2 = registerEquipment(admin, activeCatId, "Bike B", null);

        mockMvc.perform(put("/api/v1/equipment/{id}", id2)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(activeCatId, "Bike B", null, null, "SN-SHARED", null, 0L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EQUIPMENT_SERIAL"));
    }

    @Test
    void update_allowsSameSerialOnSameEquipment() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, activeCatId, "Bike Own", "SN-OWN");

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(activeCatId, "Bike Own Updated", null, null, "SN-OWN", null, 0L)))
                .andExpect(status().isOk());
    }


    @Test
    void update_returns403_forReceptionist() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, activeCatId, "AuthTest2", null);

        MockHttpSession recept = loginAsReceptionist();
        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .session(recept).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(activeCatId, "AuthTest2", null, null, null, null, 0L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_returns401_unauthenticated() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, activeCatId, "AuthTest3", null);

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(activeCatId, "AuthTest3", null, null, null, null, 0L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_returns403_missingCsrf() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, activeCatId, "AuthTest4", null);

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(activeCatId, "AuthTest4", null, null, null, null, 0L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_persistsAuditEntry() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, activeCatId, "AuditUpdate", null);

        mockMvc.perform(put("/api/v1/equipment/{id}", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(activeCatId, "AuditUpdate v2", null, null, null, null, 0L)))
                .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries " +
                "where action_code='EQUIPMENT_UPDATED' and resource_id=?",
                Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    private UUID registerEquipment(MockHttpSession session, UUID catId, String name, String serial)
            throws Exception {
        String body = serial != null
                ? "{\"categoryId\":\"" + catId + "\",\"name\":\"" + name + "\",\"serialNumber\":\"" + serial + "\"}"
                : "{\"categoryId\":\"" + catId + "\",\"name\":\"" + name + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/equipment")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

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

    private String equipmentCode(UUID id) {
        return jdbcTemplate.queryForObject(
                "select equipment_code from gym.equipment where id=?", String.class, id);
    }

    private String updateBody(UUID catId, String name, String manufacturer, String model,
                               String serial, String location, long version) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"categoryId\":\"").append(catId).append("\"");
        sb.append(",\"name\":\"").append(name).append("\"");
        if (manufacturer != null) sb.append(",\"manufacturer\":\"").append(manufacturer).append("\"");
        if (model != null)        sb.append(",\"model\":\"").append(model).append("\"");
        if (serial != null)       sb.append(",\"serialNumber\":\"").append(serial).append("\"");
        if (location != null)     sb.append(",\"location\":\"").append(location).append("\"");
        sb.append(",\"version\":").append(version);
        sb.append("}");
        return sb.toString();
    }
}
