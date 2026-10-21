package io.github.guillermodubon.coachgym.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class MembershipPeriodQueryApiIntegrationTest extends AbstractMembershipApiIntegrationTest {

    private static final UUID INITIAL_BRANCH_ID =
            UUID.fromString("7b0bf7d5-5184-43d2-8f9a-200000000002");

    @Test
    void pagesImmutablePeriodSnapshotsAndReadsThemWithoutBusinessWrites() throws Exception {
        mockMvc.perform(get("/api/v1/memberships/{membershipId}/periods", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        MockHttpSession admin = loginAsAdmin();
        selectBranch(admin, INITIAL_BRANCH_ID);
        UUID clientId = createClient(admin, uniqueValue("period-history") + "@example.test");
        String originalPlanName = uniqueValue("Original plan");
        UUID originalPlanId = createPlan(admin, originalPlanName, "42.50", "USD");
        MvcResult created = mockMvc.perform(post("/api/v1/memberships")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(membershipBody(clientId, originalPlanId, null, "2026-09-01")))
                .andExpect(status().isCreated())
                .andReturn();
        UUID membershipId = responseId(created);
        UUID originalPeriodId = UUID.fromString(JsonPath.read(
                created.getResponse().getContentAsString(), "$.currentPeriod.id"));

        createBranch(admin);
        String renewedPlanName = uniqueValue("Renewed plan");
        UUID renewedPlanId = createLongerPlan(admin, renewedPlanName, "95.00");
        setPlanCoverageToAllBranches(admin, renewedPlanId);
        UUID promotionId = createLongLivedPromotion(admin);
        replaceEligiblePlans(admin, promotionId, 0, renewedPlanId);

        MvcResult renewed = mockMvc.perform(post("/api/v1/memberships/{id}/renew", membershipId)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "membershipPlanId": "%s",
                                  "promotionId": "%s",
                                  "startsOn": null,
                                  "version": 0
                                }
                                """.formatted(renewedPlanId, promotionId)))
                .andExpect(status().isOk())
                .andReturn();
        UUID renewedPeriodId = UUID.fromString(JsonPath.read(
                renewed.getResponse().getContentAsString(), "$.currentPeriod.id"));

        mockMvc.perform(post("/api/v1/memberships/{id}/freeze", membershipId)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startsOn": "2026-10-05",
                                  "plannedEndsOn": "2026-10-10",
                                  "reason": "Historical period snapshot test",
                                  "version": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
        mockMvc.perform(post("/api/v1/memberships/{id}/reactivate", membershipId)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reactivatedOn": "2026-10-10",
                                  "version": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        java.time.LocalDate persistedEffectiveEnd = jdbcTemplate.queryForObject(
                "select effective_ends_on from gym.membership_periods where id = ?",
                java.time.LocalDate.class,
                renewedPeriodId);
        Long persistedPeriodVersion = jdbcTemplate.queryForObject(
                "select version from gym.membership_periods where id = ?",
                Long.class,
                renewedPeriodId);

        mockMvc.perform(post("/api/v1/memberships/{id}/cancel", membershipId)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cancelledOn": "2026-10-19",
                                  "reason": "History integration test",
                                  "version": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/memberships/{membershipId}/periods", membershipId)
                        .session(admin)
                        .param("direction", "ASC")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(originalPeriodId.toString()))
                .andExpect(jsonPath("$.items[0].periodNumber").value(1))
                .andExpect(jsonPath("$.items[0].pricing.planName").value(originalPlanName))
                .andExpect(jsonPath("$.items[0].pricing.listPrice").value(42.50))
                .andExpect(jsonPath("$.items[0].pricing.finalPrice").value(42.50))
                .andExpect(jsonPath("$.items[0].coverage.scopeSnapshot").value("SINGLE_BRANCH"))
                .andExpect(jsonPath("$.items[0].coverage.coveredBranchCount").value(1))
                .andExpect(jsonPath("$.items[0].coverage.branchIds").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/v1/memberships/{membershipId}/periods", membershipId)
                        .session(admin)
                        .param("page", "1")
                        .param("size", "1")
                        .param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(renewedPeriodId.toString()))
                .andExpect(jsonPath("$.items[0].periodNumber").value(2))
                .andExpect(jsonPath("$.items[0].pricing.planName").value(renewedPlanName))
                .andExpect(jsonPath("$.items[0].pricing.durationValue").value(2))
                .andExpect(jsonPath("$.items[0].pricing.listPrice").value(95.00))
                .andExpect(jsonPath("$.items[0].pricing.discountAmount").value(9.50))
                .andExpect(jsonPath("$.items[0].pricing.finalPrice").value(85.50))
                .andExpect(jsonPath("$.items[0].pricing.promotion.promotionId").value(promotionId.toString()))
                .andExpect(jsonPath("$.items[0].coverage.scopeSnapshot").value("ALL_BRANCHES"))
                .andExpect(jsonPath("$.items[0].coverage.coveredBranchCount").value(org.hamcrest.Matchers.greaterThan(1)))
                .andExpect(jsonPath("$.items[0].coverage.branchIds").doesNotExist());

        mockMvc.perform(get("/api/v1/memberships/{membershipId}/periods/{periodId}",
                                membershipId, originalPeriodId)
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(originalPeriodId.toString()))
                .andExpect(jsonPath("$.pricing.planName").value(originalPlanName))
                .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(get("/api/v1/memberships/{membershipId}/periods", membershipId)
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(renewedPeriodId.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
        mockMvc.perform(get("/api/v1/memberships/{membershipId}/periods", membershipId)
                        .session(admin)
                        .param("page", "10")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        MvcResult renewedPeriodDetail = mockMvc.perform(get("/api/v1/memberships/{membershipId}/periods/{periodId}",
                                membershipId, renewedPeriodId)
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(renewedPeriodId.toString()))
                .andExpect(jsonPath("$.effectiveEndsOn").value(persistedEffectiveEnd.toString()))
                .andExpect(jsonPath("$.version").value(persistedPeriodVersion.intValue()))
                .andReturn();
        BigDecimal returnedFinalPrice = JsonPath.parse(
                        renewedPeriodDetail.getResponse().getContentAsString())
                .read("$.pricing.finalPrice", BigDecimal.class);
        assertThat(returnedFinalPrice).isEqualByComparingTo(new BigDecimal("85.50"));

        UUID otherClientId = createClient(admin, uniqueValue("wrong-period-parent") + "@example.test");
        UUID otherMembershipId = createMembership(admin, otherClientId, originalPlanId);
        mockMvc.perform(get("/api/v1/memberships/{membershipId}/periods/{periodId}",
                                otherMembershipId, originalPeriodId)
                        .session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_PERIOD_NOT_FOUND"));

        long membershipVersion = jdbcTemplate.queryForObject(
                "select version from gym.memberships where id = ?", Long.class, membershipId);
        int periodCount = jdbcTemplate.queryForObject(
                "select count(*) from gym.membership_periods where membership_id = ?",
                Integer.class, membershipId);
        mockMvc.perform(get("/api/v1/memberships/{membershipId}/periods", membershipId)
                        .session(admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/memberships/{membershipId}/periods/{periodId}",
                                membershipId, renewedPeriodId)
                        .session(admin))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select version from gym.memberships where id = ?", Long.class, membershipId))
                .isEqualTo(membershipVersion);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.membership_periods where membership_id = ?",
                Integer.class, membershipId))
                .isEqualTo(periodCount);

        mockMvc.perform(get("/api/v1/memberships/{membershipId}/periods", membershipId)
                        .session(admin)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
        mockMvc.perform(get("/api/v1/memberships/{membershipId}/periods", membershipId)
                        .session(admin)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
    }

    @Test
    void hidesMembershipHistoryOutsideTheActorBranchAndAllowsReceptionistReadsWithoutCsrf()
            throws Exception {
        MockHttpSession admin = loginAsAdmin();
        selectBranch(admin, INITIAL_BRANCH_ID);
        UUID clientId = createClient(admin, uniqueValue("branch-period") + "@example.test");
        UUID planId = createPlan(admin, uniqueValue("branch period plan"), "30.00", "USD");
        UUID membershipId = createMembership(admin, clientId, planId);

        UUID otherBranchId = createBranch(admin);
        setPlanCoverageToAllBranches(admin, planId);
        selectBranch(admin, otherBranchId);
        UUID otherClientId = createClient(admin, uniqueValue("branch-period-other") + "@example.test");
        UUID otherMembershipId = createMembership(admin, otherClientId, planId);

        MockHttpSession receptionist = loginAsReceptionist();
        mockMvc.perform(get("/api/v1/memberships/{membershipId}/periods", membershipId)
                        .session(receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(get("/api/v1/memberships/{membershipId}/periods", otherMembershipId)
                        .session(receptionist))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/memberships/{membershipId}/periods/{periodId}",
                                otherMembershipId, UUID.randomUUID())
                        .session(receptionist))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private UUID createMembership(MockHttpSession session, UUID clientId, UUID planId)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/memberships")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(membershipBody(clientId, planId, null, "2026-09-01")))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private UUID createLongerPlan(MockHttpSession session, String name, String price)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/plans")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "Second period snapshot plan.",
                                  "durationValue": 2,
                                  "durationUnit": "MONTH",
                                  "listPrice": %s,
                                  "currency": "USD"
                                }
                                """.formatted(name, price)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private UUID createLongLivedPromotion(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/promotions")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "Promotion captured in a historical period.",
                                  "discountType": "PERCENTAGE",
                                  "discountValue": 10.00,
                                  "currency": null,
                                  "validFrom": "2026-01-01",
                                  "validUntil": "2027-12-31"
                                }
                                """.formatted(uniqueValue("History promo"))))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private UUID createBranch(MockHttpSession admin) throws Exception {
        String code = "PH-" + UUID.randomUUID().toString().substring(0, 8);
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
                                """.formatted(code, uniqueValue("Period branch"))))
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
