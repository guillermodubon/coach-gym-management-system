package io.github.guillermodubon.coachgym.membership;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class MembershipQueryApiIntegrationTest extends AbstractMembershipApiIntegrationTest {

    private static final UUID INITIAL_BRANCH_ID =
            UUID.fromString("7b0bf7d5-5184-43d2-8f9a-200000000002");

    @Test
    void returnsBoundedFilteredMembershipPagesWithPersistedPeriodAndCoverage() throws Exception {
        mockMvc.perform(get("/api/v1/memberships"))
                .andExpect(status().isUnauthorized());

        MockHttpSession admin = loginAsAdmin();
        selectBranch(admin, INITIAL_BRANCH_ID);
        UUID clientId = createClient(admin, uniqueValue("membership-list") + "@example.test");
        String planName = uniqueValue("List snapshot plan");
        UUID planId = createPlan(admin, planName, "42.50", "USD");
        UUID membershipId = createMembership(admin, clientId, planId);

        MvcResult result = mockMvc.perform(get("/api/v1/memberships")
                        .session(admin)
                        .param("clientId", clientId.toString())
                        .param("status", "ACTIVE")
                        .param("sort", "MEMBERSHIP_CODE")
                        .param("direction", "ASC")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(membershipId.toString()))
                .andExpect(jsonPath("$.items[0].clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].registeredAtBranchId")
                        .value(INITIAL_BRANCH_ID.toString()))
                .andExpect(jsonPath("$.items[0].currentPeriod.periodNumber").value(1))
                .andExpect(jsonPath("$.items[0].currentPeriod.source").value("INITIAL"))
                .andExpect(jsonPath("$.items[0].currentPeriod.pricing.planName").value(planName))
                .andExpect(jsonPath("$.items[0].currentPeriod.pricing.listPrice").value(42.50))
                .andExpect(jsonPath("$.items[0].currentPeriod.pricing.currency").value("USD"))
                .andExpect(jsonPath("$.items[0].currentPeriod.pricing.finalPrice").value(42.50))
                .andExpect(jsonPath("$.items[0].currentPeriod.coverage.scopeSnapshot")
                        .value("SINGLE_BRANCH"))
                .andExpect(jsonPath("$.items[0].currentPeriod.coverage.coveredBranchCount").value(1))
                .andExpect(jsonPath("$.items[0].currentPeriod.coverage.branchIds").doesNotExist())
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.items[0].clientName").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andReturn();

        String membershipCode = JsonPath.read(
                result.getResponse().getContentAsString(), "$.items[0].membershipCode");
        mockMvc.perform(get("/api/v1/memberships")
                        .session(admin)
                        .param("search", membershipCode.toLowerCase(java.util.Locale.ROOT))
                        .param("clientId", clientId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(membershipId.toString()));

        mockMvc.perform(get("/api/v1/memberships")
                        .session(admin)
                        .param("search", "%")
                        .param("clientId", clientId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/memberships")
                        .session(admin)
                        .param("clientId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/memberships")
                        .session(admin)
                        .param("clientId", clientId.toString())
                        .param("page", "10")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        mockMvc.perform(get("/api/v1/memberships")
                        .session(admin)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
        mockMvc.perform(get("/api/v1/memberships")
                        .session(admin)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
    }

    @Test
    void constrainsListAndTotalsToResolvedBranchForAdminAndReceptionist() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        selectBranch(admin, INITIAL_BRANCH_ID);
        UUID firstClientId = createClient(
                admin, uniqueValue("branch-one-membership") + "@example.test");
        String planName = uniqueValue("Multi branch plan");
        UUID planId = createPlan(admin, planName, "30.00", "USD");
        UUID firstMembershipId = createMembership(admin, firstClientId, planId);

        UUID otherBranchId = createBranch(admin);
        setPlanCoverageToAllBranches(admin, planId);
        selectBranch(admin, otherBranchId);
        UUID otherClientId = createClient(
                admin, uniqueValue("branch-two-membership") + "@example.test");
        UUID otherMembershipId = createMembership(admin, otherClientId, planId);

        mockMvc.perform(get("/api/v1/memberships")
                        .session(admin)
                        .param("clientId", otherClientId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(otherMembershipId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/memberships")
                        .session(admin)
                        .param("clientId", firstClientId.toString())
                        .param("branchId", INITIAL_BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(firstMembershipId.toString()))
                .andExpect(jsonPath("$.items[0].currentPeriod.coverage.scopeSnapshot")
                        .value("SINGLE_BRANCH"))
                .andExpect(jsonPath("$.items[0].currentPeriod.coverage.coveredBranchCount").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(delete("/api/v1/me/branch-context")
                        .session(admin)
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/memberships").session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_BRANCH_UNAVAILABLE"));
        mockMvc.perform(get("/api/v1/memberships")
                        .session(admin)
                        .param("clientId", otherClientId.toString())
                        .param("branchId", otherBranchId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(otherMembershipId.toString()))
                .andExpect(jsonPath("$.items[0].currentPeriod.coverage.scopeSnapshot")
                        .value("ALL_BRANCHES"))
                .andExpect(jsonPath("$.items[0].currentPeriod.coverage.coveredBranchCount")
                        .value(org.hamcrest.Matchers.greaterThan(1)));

        MockHttpSession receptionist = loginAsReceptionist();
        selectBranch(receptionist, INITIAL_BRANCH_ID);
        mockMvc.perform(get("/api/v1/memberships")
                        .session(receptionist)
                        .param("clientId", firstClientId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(firstMembershipId.toString()));
        mockMvc.perform(get("/api/v1/memberships")
                        .session(receptionist)
                        .param("clientId", otherClientId.toString())
                        .param("branchId", otherBranchId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private UUID createMembership(
            MockHttpSession session,
            UUID clientId,
            UUID planId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/memberships")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(membershipBody(clientId, planId, null, "2026-09-01")))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private UUID createBranch(MockHttpSession admin) throws Exception {
        String code = "Q-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/branches")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "%s",
                                  "city": "San Salvador",
                                  "countryCode": "SV"
                                }
                                """.formatted(code, uniqueValue("Membership query branch"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private void selectBranch(MockHttpSession session, UUID branchId) throws Exception {
        mockMvc.perform(put("/api/v1/me/branch-context")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"%s\"}".formatted(branchId)))
                .andExpect(status().isOk());
    }

    private void setPlanCoverageToAllBranches(MockHttpSession admin, UUID planId) throws Exception {
        mockMvc.perform(put("/api/v1/plans/{id}/branch-coverage", planId)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scope": "ALL_BRANCHES",
                                  "branchIds": [],
                                  "expectedVersion": 0
                                }
                                """))
                .andExpect(status().isOk());
    }

}
