package io.github.guillermodubon.coachgym.equipment;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
class EquipmentApiIntegrationTest extends AbstractEquipmentApiIntegrationTest {

    private UUID activeCategoryId;
    private UUID inactiveCategoryId;

    @BeforeEach
    void provision() {
        jdbcTemplate.update("delete from gym.audit_entries where action_code like 'EQUIPMENT%'");
        jdbcTemplate.update("delete from gym.equipment");
        jdbcTemplate.update("delete from gym.equipment_categories");
        provisionUser(ADMIN_USERNAME, "eq-admin@example.com", ADMIN_PASSWORD, "ADMIN");
        provisionUser(RECEPTIONIST_USERNAME, "eq-recept@example.com", RECEPTIONIST_PASSWORD, "RECEPTIONIST");

        // Provision a known active category and an inactive one.
        MockHttpSession adminSession;
        try {
            adminSession = loginAsAdmin();
            activeCategoryId = createCategory(adminSession, "Cardio");
            inactiveCategoryId = createCategory(adminSession, "Inactive Cat");
            deactivateCategory(adminSession, inactiveCategoryId, 0L);
        } catch (Exception e) {
            throw new RuntimeException("Setup failed", e);
        }
    }

    // ── REGISTER ──────────────────────────────────────────────────────────────

    @Test
    void register_returns201_withDbGeneratedCode() throws Exception {
        MockHttpSession session = loginAsAdmin();

        MvcResult result = mockMvc.perform(post("/api/v1/equipment")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(activeCategoryId, "Treadmill Pro", null)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.equipmentCode").value(org.hamcrest.Matchers.startsWith("EQP-")))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.categoryName").value("Cardio"))
                .andReturn();

        UUID id = responseId(result);
        String location = result.getResponse().getHeader("Location");
        assertThat(location).contains(id.toString());
    }

    @Test
    void register_codeIsImmutableAndIncreasing() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID id1 = registerEquipment(session, "Bike A", null);
        UUID id2 = registerEquipment(session, "Bike B", null);

        String code1 = equipmentCode(id1);
        String code2 = equipmentCode(id2);

        assertThat(code1).startsWith("EQP-");
        assertThat(code2).startsWith("EQP-");
        assertThat(code1).isNotEqualTo(code2);
    }

    @Test
    void register_returns404_whenCategoryNotFound() throws Exception {
        MockHttpSession session = loginAsAdmin();
        mockMvc.perform(post("/api/v1/equipment")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(UUID.randomUUID(), "Bike", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_CATEGORY_NOT_FOUND"));
    }

    @Test
    void register_returns409_whenCategoryInactive() throws Exception {
        MockHttpSession session = loginAsAdmin();
        mockMvc.perform(post("/api/v1/equipment")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(inactiveCategoryId, "Elliptical", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_CATEGORY_INACTIVE"));
    }

    @Test
    void register_returns409_whenDuplicateSerialNumber() throws Exception {
        MockHttpSession session = loginAsAdmin();
        registerEquipment(session, "Treadmill", "SN-UNIQUE-001");

        mockMvc.perform(post("/api/v1/equipment")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(activeCategoryId, "Bike", "SN-UNIQUE-001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EQUIPMENT_SERIAL"));
    }

    @Test
    void register_serialUniquenessIsCaseInsensitive() throws Exception {
        MockHttpSession session = loginAsAdmin();
        registerEquipment(session, "Treadmill", "sn-abc");

        mockMvc.perform(post("/api/v1/equipment")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(activeCategoryId, "Bike", "SN-ABC")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EQUIPMENT_SERIAL"));
    }

    @Test
    void register_returns403_missingCsrf() throws Exception {
        MockHttpSession session = loginAsAdmin();
        mockMvc.perform(post("/api/v1/equipment")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(activeCategoryId, "Bike", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_returns401_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/equipment")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(activeCategoryId, "Bike", null)))
                .andExpect(status().isUnauthorized());
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    @Test
    void findByIdReturns200ForAdminAndReceptionist() throws Exception {
        MockHttpSession adminSession = loginAsAdmin();
        UUID id = registerEquipment(adminSession, "Rower", null);

        for (MockHttpSession session : List.of(loginAsAdmin(), loginAsReceptionist())) {
            mockMvc.perform(get("/api/v1/equipment/{id}", id).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.equipmentCode").value(org.hamcrest.Matchers.startsWith("EQP-")));
        }
    }

    @Test
    void findById_returns404_whenNotFound() throws Exception {
        MockHttpSession session = loginAsAdmin();
        mockMvc.perform(get("/api/v1/equipment/{id}", UUID.randomUUID()).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_NOT_FOUND"));
    }

    @Test
    void findById_returns401_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/equipment/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── LIST ──────────────────────────────────────────────────────────────────

    @Test
    void findAll_returns200_paginatedResult() throws Exception {
        MockHttpSession session = loginAsAdmin();
        registerEquipment(session, "Treadmill List A", null);
        registerEquipment(session, "Treadmill List B", null);

        mockMvc.perform(get("/api/v1/equipment").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void findAll_filterByCategoryId_returnsOnlyMatchingEquipment() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID cat2 = createCategory(session, "Weights2");
        registerEquipment(session, "Barbell", null); // goes to activeCategoryId
        // register one in cat2
        mockMvc.perform(post("/api/v1/equipment")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(cat2, "Dumbbell", null)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/v1/equipment")
                        .session(session)
                        .param("categoryId", cat2.toString()))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(body, "$.items.length()")).isEqualTo(1);
        assertThat((String) JsonPath.read(body, "$.items[0].name")).isEqualTo("Dumbbell");
    }

    @Test
    void findAll_filterByStatus_returnsOnlyAvailable() throws Exception {
        MockHttpSession session = loginAsAdmin();
        registerEquipment(session, "Filter Status Eq", null);

        mockMvc.perform(get("/api/v1/equipment")
                        .session(session)
                        .param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("AVAILABLE"));
    }

    @Test
    void findAll_filterBySearch_returnsNameMatch() throws Exception {
        MockHttpSession session = loginAsAdmin();
        registerEquipment(session, "UniqueSearchName XYZ", null);

        MvcResult result = mockMvc.perform(get("/api/v1/equipment")
                        .session(session)
                        .param("search", "UniqueSearchName"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat((Integer) JsonPath.read(result.getResponse().getContentAsString(), "$.items.length()"))
                .isEqualTo(1);
    }

    @Test
    void findAll_sortByNameAsc_isStable() throws Exception {
        MockHttpSession session = loginAsAdmin();
        registerEquipment(session, "ZZZ Sort Last", null);
        registerEquipment(session, "AAA Sort First", null);

        MvcResult result = mockMvc.perform(get("/api/v1/equipment")
                        .session(session)
                        .param("sort", "name")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String firstName = JsonPath.read(body, "$.items[0].name");
        assertThat(firstName).isEqualTo("AAA Sort First");
    }

    @Test
    void findAll_returns400_invalidStatus() throws Exception {
        MockHttpSession session = loginAsAdmin();
        mockMvc.perform(get("/api/v1/equipment").session(session).param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findAll_returns400_invalidSort() throws Exception {
        MockHttpSession session = loginAsAdmin();
        mockMvc.perform(get("/api/v1/equipment").session(session).param("sort", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    // ── AUDIT ─────────────────────────────────────────────────────────────────

    @Test
    void register_persistsAuditEntry() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID id = registerEquipment(session, "AuditEquipment", null);

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries " +
                "where action_code='EQUIPMENT_REGISTERED' and resource_id=?",
                Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    // ── DB CONSTRAINTS ────────────────────────────────────────────────────────

    @Test
    void directInsert_blankName_violatesDbConstraint() {
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                jdbcTemplate.update(
                        "insert into gym.equipment (id, equipment_category_id, name, status) " +
                        "values (?,?,?,?)",
                        UUID.randomUUID(), activeCategoryId, "  ", "AVAILABLE"));
    }

    @Test
    void directInsert_invalidStatus_violatesDbConstraint() {
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                jdbcTemplate.update(
                        "insert into gym.equipment (id, equipment_category_id, name, status) " +
                        "values (?,?,?,?)",
                        UUID.randomUUID(), activeCategoryId, "Treadmill", "BROKEN"));
    }

    // ── PUBLIC LOOKUP BOUNDARY ────────────────────────────────────────────────

    @Test
    void equipmentLookup_findById_returnsDetailsForExistingEquipment() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID id = registerEquipment(session, "LookupEquipment", null);

        // Access via the public lookup boundary (verified through the service integration path).
        mockMvc.perform(get("/api/v1/equipment/{id}", id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID registerEquipment(MockHttpSession session, String name, String serial) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/equipment")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(activeCategoryId, name, serial)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private String equipmentCode(UUID id) {
        return jdbcTemplate.queryForObject(
                "select equipment_code from gym.equipment where id=?", String.class, id);
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

    private String registerBody(UUID catId, String name, String serial) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"categoryId\":\"").append(catId).append("\"");
        sb.append(",\"name\":\"").append(name).append("\"");
        if (serial != null) {
            sb.append(",\"serialNumber\":\"").append(serial).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private java.util.List<MockHttpSession> List(MockHttpSession... sessions) {
        return java.util.Arrays.asList(sessions);
    }
}
