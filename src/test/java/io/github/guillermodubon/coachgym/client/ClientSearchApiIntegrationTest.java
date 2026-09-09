package io.github.guillermodubon.coachgym.client;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientSearchApiIntegrationTest
        extends AbstractClientProfileApiIntegrationTest {

    @Test
    void currentRolesCanSearchByCodeNamePhoneAndEmail() throws Exception {
        var clientId = insertProfileClient(
                "BlockEight",
                "Searchable",
                "block.eight.search@example.com",
                "+50370008008");
        String code = clientCode(clientId);

        mockMvc.perform(get("/api/v1/clients")
                        .session(loginAsAdmin())
                        .param("search", code)
                        .param("page", "0")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id")
                        .value(hasItem(clientId.toString())));

        mockMvc.perform(get("/api/v1/clients")
                        .session(loginAsReceptionist())
                        .param("search", "blockeight searchable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id")
                        .value(hasItem(clientId.toString())));

        mockMvc.perform(get("/api/v1/clients")
                        .session(loginAsReceptionist())
                        .param("search", "+50370008008"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id")
                        .value(hasItem(clientId.toString())));

        mockMvc.perform(get("/api/v1/clients")
                        .session(loginAsAdmin())
                        .param("search", "BLOCK.EIGHT.SEARCH@EXAMPLE.COM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id")
                        .value(hasItem(clientId.toString())));
    }

    @Test
    void supportsStatusPaginationAndAllowlistedSorting() throws Exception {
        insertProfileClient(
                "BlockEight",
                "Paging",
                "block.eight.paging@example.com",
                "+50370008009");

        mockMvc.perform(get("/api/v1/clients")
                        .session(loginAsAdmin())
                        .param("status", "ACTIVE")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "LAST_NAME")
                        .param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void rejectsInvalidPaginationAndUnknownSortValues() throws Exception {
        mockMvc.perform(get("/api/v1/clients")
                        .session(loginAsAdmin())
                        .param("size", "101"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/clients")
                        .session(loginAsAdmin())
                        .param("sort", "DROP_TABLE"))
                .andExpect(status().isBadRequest());
    }
}
