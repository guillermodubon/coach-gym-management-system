package io.github.guillermodubon.coachgym.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class EquipmentCategorySecurityApiIntegrationTest extends AbstractEquipmentApiIntegrationTest {
    @BeforeEach
    void provision() {
        provisionUser(ADMIN_USERNAME, "eq-admin@test.local", ADMIN_PASSWORD, "ADMIN");
        provisionUser(MAINTENANCE_USERNAME, "eq-maintenance@test.local", MAINTENANCE_PASSWORD, "MAINTENANCE");
        provisionUser(RECEPTIONIST_USERNAME, "eq-receptionist@test.local", RECEPTIONIST_PASSWORD, "RECEPTIONIST");
        jdbcTemplate.update("delete from gym.audit_entries where action_code like 'EQUIPMENT_CATEGORY%'");
        jdbcTemplate.update("delete from gym.equipment_status_history");
        jdbcTemplate.update("delete from gym.equipment");
        jdbcTemplate.update("delete from gym.equipment_categories");
    }

    @Test
    void maintenanceAndReceptionistCanReadButCannotCreate() throws Exception {
        for (MockHttpSession session : new MockHttpSession[]{loginAsMaintenance(), loginAsReceptionist()}) {
            mockMvc.perform(get("/api/v1/equipment-categories").session(session))
                    .andExpect(status().isOk());
            long categories = count("gym.equipment_categories");
            long audits = auditCount();
            mockMvc.perform(post("/api/v1/equipment-categories").session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied\"}"))
                    .andExpect(status().isForbidden());
            assertThat(count("gym.equipment_categories")).isEqualTo(categories);
            assertThat(auditCount()).isEqualTo(audits);
        }
    }

    @Test
    void unauthenticatedAndMissingCsrfCreateHaveNoSideEffects() throws Exception {
        long categories = count("gym.equipment_categories");
        long audits = auditCount();
        mockMvc.perform(post("/api/v1/equipment-categories").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied\"}"))
                .andExpect(status().isUnauthorized());
        MockHttpSession admin = loginAsAdmin();
        mockMvc.perform(post("/api/v1/equipment-categories").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied\"}"))
                .andExpect(status().isForbidden());
        assertThat(count("gym.equipment_categories")).isEqualTo(categories);
        assertThat(auditCount()).isEqualTo(audits);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private long auditCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries where action_code like 'EQUIPMENT_CATEGORY%'", Long.class);
    }
}
