package io.github.guillermodubon.coachgym.client;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.TestReporter;

import static org.hamcrest.Matchers.hasItem;
import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void listsDefaultToTheActiveBranchAndRequireAuthorizedExplicitBranchFilters()
            throws Exception {
        UUID branchB = UUID.randomUUID();
        String branchCode = "SEARCH_" + branchB.toString()
                .replace("-", "").substring(0, 8).toUpperCase();
        jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, version)
                values (?, '7b0bf7d5-5184-43d2-8f9a-200000000001', ?,
                        'Search Branch', 'America/El_Salvador', 'ACTIVE', 0)
                """, branchB, branchCode);
        var branchBClient = insertProfileClient(
                "Scoped", "Client", "branch.b.search@example.com", "+50370008199");
        try {
            jdbcTemplate.update(
                    "update gym.clients set home_branch_id = ? where id = ?",
                    branchB, branchBClient);

            mockMvc.perform(get("/api/v1/clients")
                            .session(loginAsAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id")
                            .value(org.hamcrest.Matchers.not(
                                    hasItem(branchBClient.toString()))));

            mockMvc.perform(get("/api/v1/clients")
                            .session(loginAsAdmin())
                            .param("branchId", branchB.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id")
                            .value(hasItem(branchBClient.toString())))
                    .andExpect(jsonPath("$.items[0].homeBranchId")
                            .value(branchB.toString()));

            mockMvc.perform(get("/api/v1/clients")
                            .session(loginAsReceptionist())
                            .param("branchId", branchB.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.detail")
                            .value("The requested resource was not found."));

            mockMvc.perform(get("/api/v1/clients/{id}/profile", branchBClient)
                            .session(loginAsReceptionist()))
                    .andExpect(status().isNotFound());
        } finally {
            jdbcTemplate.update("delete from gym.clients where id = ?", branchBClient);
            jdbcTemplate.update("""
                    update gym.gym_branches
                    set status = 'INACTIVE', version = version + 1
                    where id = ?
                    """, branchB);
        }
    }

    @Test
    void hidesUnauthorizedBranchFiltersAcrossOperationalSearchEndpoints()
            throws Exception {
        UUID unavailableBranchId = UUID.randomUUID();
        var session = loginAsAdmin();
        List<String> operationalSearchPaths = List.of(
                "/api/v1/clients",
                "/api/v1/payments",
                "/api/v1/access/records",
                "/api/v1/equipment",
                "/api/v1/incidents",
                "/api/v1/maintenances",
                "/api/v1/email-deliveries");

        for (String path : operationalSearchPaths) {
            var result = mockMvc.perform(get(path)
                            .session(session)
                            .param("branchId", unavailableBranchId.toString()))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).as(path).isEqualTo(404);
            assertThat(result.getResponse().getContentAsString())
                    .as(path)
                    .contains("\"code\":\"RESOURCE_NOT_FOUND\"")
                    .contains("The requested resource was not found.");
        }
    }

    @Test
    void branchFilteredClientListHasAnIndexedBoundedPerformanceBaseline(
            TestReporter reporter) throws Exception {
        UUID branchId = UUID.randomUUID();
        String branchCode = "PERF_" + branchId.toString()
                .replace("-", "").substring(0, 8).toUpperCase();
        String emailPrefix = "branch-scope-perf-"
                + branchId.toString().replace("-", "") + "-";
        jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, version)
                values (?, '7b0bf7d5-5184-43d2-8f9a-200000000001', ?,
                        'Performance Branch', 'America/El_Salvador', 'ACTIVE', 0)
                """, branchId, branchCode);

        try {
            jdbcTemplate.update("""
                    insert into gym.clients
                        (id, first_name, last_name, email, phone, date_of_birth,
                         status, created_by_user_id, updated_by_user_id,
                         home_branch_id)
                    select gen_random_uuid(), 'Performance',
                           'Client-' || lpad(sequence_number::text, 6, '0'),
                           ? || sequence_number::text || '@example.test',
                           '+503' || lpad(sequence_number::text, 8, '0'),
                           date '1990-01-01', 'ACTIVE', ?, ?,
                           case when sequence_number % 20 = 0
                                then ?::uuid
                                else '7b0bf7d5-5184-43d2-8f9a-200000000002'::uuid
                           end
                    from generate_series(1, 20000) as sequence_number
                    """, emailPrefix, adminId, adminId, branchId);
            jdbcTemplate.execute("analyze gym.clients");

            List<String> plan = jdbcTemplate.queryForList("""
                    explain (analyze, buffers, format text)
                    select c.id
                    from gym.clients c
                    where c.home_branch_id = ?
                    order by c.last_name asc, c.first_name asc, c.id asc
                    limit 25 offset 0
                    """, String.class, branchId);
            String explain = String.join("\n", plan);
            reporter.publishEntry("branch-filtered-client-query-plan", explain);
            assertThat(explain).contains("home_branch_id");

            var session = loginAsAdmin();
            for (int sample = 0; sample < 5; sample++) {
                mockMvc.perform(get("/api/v1/clients")
                                .session(session)
                                .param("branchId", branchId.toString())
                                .param("page", "0")
                                .param("size", "25"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items.length()").value(25));
            }

            List<Long> elapsedMillis = new ArrayList<>();
            for (int sample = 0; sample < 25; sample++) {
                long startedAt = System.nanoTime();
                mockMvc.perform(get("/api/v1/clients")
                                .session(session)
                                .param("branchId", branchId.toString())
                                .param("page", "0")
                                .param("size", "25"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items.length()").value(25));
                elapsedMillis.add(Duration.ofNanos(System.nanoTime() - startedAt)
                        .toMillis());
            }
            Collections.sort(elapsedMillis);
            long p95Millis = elapsedMillis.get((int) Math.ceil(
                    elapsedMillis.size() * 0.95) - 1);
            reporter.publishEntry("branch-filtered-client-list-p95",
                    p95Millis + " ms; 20,000 representative rows, page size 25");
            assertThat(elapsedMillis).allMatch(elapsed -> elapsed >= 0L);
            assertThat(p95Millis).isLessThan(5000L);
        } finally {
            jdbcTemplate.update("delete from gym.clients where email like ?",
                    emailPrefix + "%");
            jdbcTemplate.update("""
                    update gym.gym_branches
                    set status = 'INACTIVE', version = version + 1
                    where id = ?
                    """, branchId);
            jdbcTemplate.execute("analyze gym.clients");
        }
    }
}
