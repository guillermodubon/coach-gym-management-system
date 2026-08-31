package io.github.guillermodubon.coachgym.equipment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class EquipmentQueryCompletionApiIntegrationTest extends AbstractEquipmentApiIntegrationTest {
    private MockHttpSession admin;

    @BeforeEach
    void provision() throws Exception {
        provisionUser(ADMIN_USERNAME, "eq-admin@test.local", ADMIN_PASSWORD, "ADMIN");
        jdbcTemplate.update("delete from gym.equipment_status_history");
        jdbcTemplate.update("delete from gym.equipment");
        jdbcTemplate.update("delete from gym.equipment_categories");
        admin = loginAsAdmin();
    }

    @Test
    void emptyCatalogReturnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/equipment").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void pageOutsideResultRangeReturnsEmptyItems() throws Exception {
        mockMvc.perform(get("/api/v1/equipment")
                        .session(admin).param("page", "50").param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(50));
    }

    @Test
    void maximumPageSizeIsAccepted() throws Exception {
        mockMvc.perform(get("/api/v1/equipment").session(admin).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void invalidPaginationAndDirectionAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/equipment").session(admin).param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/equipment").session(admin).param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/equipment").session(admin).param("direction", "sideways"))
                .andExpect(status().isBadRequest());
    }
}
