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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
class EquipmentLifecycleApiIntegrationTest extends AbstractEquipmentApiIntegrationTest {

    private UUID categoryId;

    @BeforeEach
    void provision() throws Exception {
        jdbcTemplate.update("delete from gym.audit_entries where action_code like 'EQUIPMENT%'");
        jdbcTemplate.update("delete from gym.equipment_status_history");
        jdbcTemplate.update("delete from gym.equipment");
        jdbcTemplate.update("delete from gym.equipment_categories");
        provisionUser(ADMIN_USERNAME, "eq-admin@example.com", ADMIN_PASSWORD, "ADMIN");
        provisionUser(RECEPTIONIST_USERNAME, "eq-recept@example.com", RECEPTIONIST_PASSWORD, "RECEPTIONIST");

        MockHttpSession admin = loginAsAdmin();
        categoryId = createCategory(admin, "Cardio");
    }

    @Test
    void outOfService_fromAvailable_returns200_statusChangedAndHistoryInserted() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, "Treadmill");

        mockMvc.perform(post("/api/v1/equipment/{id}/out-of-service", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Belt worn out\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"))
                .andExpect(jsonPath("$.version").value(1));

        // Verify history row was inserted.
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from gym.equipment_status_history where equipment_id=?",
                Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void outOfService_alreadyOutOfService_returns409StateConflict() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, "Bike B");
        transition(admin, id, "out-of-service", 0L);

        mockMvc.perform(post("/api/v1/equipment/{id}/out-of-service", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Again\",\"version\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_STATE_CONFLICT"));
    }

    @Test
    void available_fromOutOfService_returns200() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, "Rower");
        transition(admin, id, "out-of-service", 0L);

        mockMvc.perform(post("/api/v1/equipment/{id}/available", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Repaired\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void available_alreadyAvailable_returns409StateConflict() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, "Stepper");

        mockMvc.perform(post("/api/v1/equipment/{id}/available", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Already fine\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_STATE_CONFLICT"));
    }

    @Test
    void retire_fromAvailable_returns200_setsRetirementColumns() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, "Old Treadmill");

        mockMvc.perform(post("/api/v1/equipment/{id}/retire", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"End of life\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETIRED"))
                .andExpect(jsonPath("$.retiredAt").exists())
                .andExpect(jsonPath("$.retirementReason").value("End of life"));
    }

    @Test
    void retire_fromOutOfService_succeeds() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, "Old Bike");
        transition(admin, id, "out-of-service", 0L);

        mockMvc.perform(post("/api/v1/equipment/{id}/retire", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Beyond repair\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETIRED"));
    }

    @Test
    void retire_alreadyRetired_returns409Terminal() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, "Retiring Eq");
        transition(admin, id, "retire", 0L);

        mockMvc.perform(post("/api/v1/equipment/{id}/retire", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Again\",\"version\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_STATE_CONFLICT"));
    }


    @Test
    void retire_byReceptionist_returns403() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, "Restrct Eq 2");

        MockHttpSession recept = loginAsReceptionist();
        mockMvc.perform(post("/api/v1/equipment/{id}/retire", id)
                        .session(recept).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Trying\",\"version\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void outOfService_fromMaintenance_returns409StateConflict() throws Exception {
        // Direct DB insert to simulate equipment in MAINTENANCE (which the catalog cannot enter).
        MockHttpSession admin = loginAsAdmin();
        UUID id = insertEquipmentDirectly("MAINTENANCE", admin);

        mockMvc.perform(post("/api/v1/equipment/{id}/out-of-service", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Test\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_STATE_CONFLICT"));
    }

    @Test
    void outOfService_staleVersion_returns409VersionConflict() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, "OptLock Eq");

        mockMvc.perform(post("/api/v1/equipment/{id}/out-of-service", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Stale\",\"version\":99}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_VERSION_CONFLICT"));
    }

    @Test
    void outOfService_blankReason_returns400() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, "ValidationEq");

        mockMvc.perform(post("/api/v1/equipment/{id}/out-of-service", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \",\"version\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void outOfService_nullVersion_returns400() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, "ValidationEq2");

        mockMvc.perform(post("/api/v1/equipment/{id}/out-of-service", id)
                        .session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"valid reason\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void outOfService_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/equipment/{id}/out-of-service", UUID.randomUUID())
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\",\"version\":0}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void outOfService_missingCsrf_returns403() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, "CsrfEq");

        mockMvc.perform(post("/api/v1/equipment/{id}/out-of-service", id)
                        .session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\",\"version\":0}"))
                .andExpect(status().isForbidden());
    }


    @Test
    void outOfService_persistsAuditEntry() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, "AuditEq");

        transition(admin, id, "out-of-service", 0L);

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries " +
                "where action_code='EQUIPMENT_STATUS_CHANGED_TO_OUT_OF_SERVICE' and resource_id=?",
                Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void retire_persistsAuditEntry() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, "AuditRetireEq");

        transition(admin, id, "retire", 0L);

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries " +
                "where action_code='EQUIPMENT_STATUS_CHANGED_TO_RETIRED' and resource_id=?",
                Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void directInsert_statusHistory_invalidStatus_violatesConstraint() {
        UUID eqId = UUID.randomUUID();
        // Insert a valid equipment first
        jdbcTemplate.update(
                "insert into gym.equipment (id, equipment_category_id, name, status) values (?,?,?,?)",
                eqId, categoryId, "ConstraintTest", "AVAILABLE");

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                jdbcTemplate.update(
                        "insert into gym.equipment_status_history " +
                        "(id, equipment_id, new_status, occurred_at) values (?,?,?,now())",
                        UUID.randomUUID(), eqId, "BROKEN"));
    }

    @Test
    void directInsert_statusHistory_sameStatusTransition_violatesConstraint() {
        UUID eqId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into gym.equipment (id, equipment_category_id, name, status) values (?,?,?,?)",
                eqId, categoryId, "ConstraintTest2", "AVAILABLE");

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                jdbcTemplate.update(
                        "insert into gym.equipment_status_history " +
                        "(id, equipment_id, previous_status, new_status, occurred_at) " +
                        "values (?,?,?,?,now())",
                        UUID.randomUUID(), eqId, "AVAILABLE", "AVAILABLE"));
    }

    @Test
    void historyAndStatusAreAtomicWithVersion() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID id = registerEquipment(admin, "AtomicEq");

        // Valid transition
        transition(admin, id, "out-of-service", 0L);

        // Verify both the status and a history row exist
        String status = jdbcTemplate.queryForObject(
                "select status from gym.equipment where id=?", String.class, id);
        assertThat(status).isEqualTo("OUT_OF_SERVICE");

        Integer historyCount = jdbcTemplate.queryForObject(
                "select count(*) from gym.equipment_status_history where equipment_id=?",
                Integer.class, id);
        assertThat(historyCount).isEqualTo(1);
    }


    private UUID registerEquipment(MockHttpSession session, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/equipment")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private void transition(MockHttpSession session, UUID id, String endpoint, long version)
            throws Exception {
        mockMvc.perform(post("/api/v1/equipment/{id}/" + endpoint, id)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Integration test\",\"version\":" + version + "}"))
                .andExpect(status().isOk());
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

    private UUID insertEquipmentDirectly(String status, MockHttpSession session) throws Exception {
        // Register normally to get a valid category FK, then update status directly.
        UUID id = registerEquipment(session, "DirectInsert-" + status);
        jdbcTemplate.update("update gym.equipment set status=? where id=?", status, id);
        return id;
    }
}
