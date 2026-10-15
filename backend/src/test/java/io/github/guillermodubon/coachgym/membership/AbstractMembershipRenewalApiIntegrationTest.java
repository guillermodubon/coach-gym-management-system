package io.github.guillermodubon.coachgym.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

abstract class AbstractMembershipRenewalApiIntegrationTest
        extends AbstractMembershipApiIntegrationTest {

    protected UUID createMembership(
            MockHttpSession session,
            UUID clientId,
            UUID planId,
            UUID promotionId,
            String startsOn)
            throws Exception {

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/memberships")
                                        .with(csrf())
                                        .session(session)
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                membershipBody(
                                                        clientId,
                                                        planId,
                                                        promotionId,
                                                        startsOn)))
                        .andExpect(status().isCreated())
                        .andReturn();

        return responseId(result);
    }

    protected ResultActions renewMembership(
            MockHttpSession session,
            UUID membershipId,
            UUID planId,
            UUID promotionId,
            String startsOn,
            long version)
            throws Exception {

        return mockMvc.perform(
                post(
                        "/api/v1/memberships/{id}/renew",
                        membershipId)
                        .with(csrf())
                        .session(session)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(
                                renewalBody(
                                        planId,
                                        promotionId,
                                        startsOn,
                                        version)));
    }

    protected UUID renewalPeriodId(
            MvcResult result)
            throws Exception {

        return UUID.fromString(
                JsonPath.read(
                        result.getResponse()
                                .getContentAsString(),
                        "$.currentPeriod.id"));
    }

    protected UUID createRenewalPromotion(
            MockHttpSession session,
            String name)
            throws Exception {

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/promotions")
                                        .with(csrf())
                                        .session(session)
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "name": "%s",
                                                  "description": "Promotion for membership renewal integration.",
                                                  "discountType": "PERCENTAGE",
                                                  "discountValue": 10.00,
                                                  "currency": null,
                                                  "validFrom": "2026-09-01",
                                                  "validUntil": "2026-12-31"
                                                }
                                                """
                                                        .formatted(name)))
                        .andExpect(status().isCreated())
                        .andReturn();

        return responseId(result);
    }

    protected void expireMembership(
            UUID membershipId) {

        jdbcTemplate.update(
                """
                update gym.memberships
                set status = 'EXPIRED',
                    updated_at = current_timestamp,
                    version = version + 1
                where id = ?
                """,
                membershipId);
    }

    protected void freezeMembershipDirectly(
            UUID membershipId) {

        jdbcTemplate.update(
                """
                update gym.memberships
                set status = 'FROZEN',
                    updated_at = current_timestamp,
                    version = version + 1
                where id = ?
                """,
                membershipId);
    }

    protected long membershipVersion(
            UUID membershipId) {

        Long version =
                jdbcTemplate.queryForObject(
                        """
                        select version
                        from gym.memberships
                        where id = ?
                        """,
                        Long.class,
                        membershipId);

        return version == null
                ? -1
                : version;
    }

    protected String membershipStatus(
            UUID membershipId) {

        return jdbcTemplate.queryForObject(
                """
                select status
                from gym.memberships
                where id = ?
                """,
                String.class,
                membershipId);
    }

    protected int periodCount(
            UUID membershipId) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.membership_periods
                        where membership_id = ?
                        """,
                        Integer.class,
                        membershipId);

        return count == null
                ? 0
                : count;
    }

    protected int renewalStatusHistoryCount(
            UUID membershipId) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.membership_status_history
                        where membership_id = ?
                          and previous_status is not null
                        """,
                        Integer.class,
                        membershipId);

        return count == null
                ? 0
                : count;
    }

    protected int renewalAuditCount(
            UUID membershipId) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.audit_entries
                        where resource_id = ?
                          and resource_type = 'MEMBERSHIP'
                          and action_code = 'MEMBERSHIP_RENEWED'
                        """,
                        Integer.class,
                        membershipId);

        return count == null
                ? 0
                : count;
    }

    protected void assertPeriod(
            UUID membershipId,
            short periodNumber,
            String source,
            String startsOn,
            String effectiveEndsOn) {

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.membership_periods
                        where membership_id = ?
                          and period_number = ?
                          and period_source = ?
                          and starts_on = cast(? as date)
                          and effective_ends_on = cast(? as date)
                        """,
                        Integer.class,
                        membershipId,
                        periodNumber,
                        source,
                        startsOn,
                        effectiveEndsOn))
                .isEqualTo(1);
    }

    protected void assertRenewalAudit(
            UUID membershipId,
            UUID renewalPeriodId,
            int periodNumber,
            UUID promotionId,
            String previousStatus,
            String resultingStatus,
            boolean statusChanged,
            String listPrice,
            String discountAmount,
            String finalPrice) {

        assertThat(renewalAuditCount(membershipId))
                .isEqualTo(1);

        assertAuditMetadata(
                membershipId,
                "membershipPeriodId",
                renewalPeriodId.toString());

        assertAuditMetadata(
                membershipId,
                "periodNumber",
                Integer.toString(periodNumber));

        assertAuditMetadata(
                membershipId,
                "previousStatus",
                previousStatus);

        assertAuditMetadata(
                membershipId,
                "resultingStatus",
                resultingStatus);

        assertAuditMetadata(
                membershipId,
                "statusChanged",
                Boolean.toString(statusChanged));

        assertAuditMetadata(
                membershipId,
                "listPrice",
                listPrice);

        assertAuditMetadata(
                membershipId,
                "discountAmount",
                discountAmount);

        assertAuditMetadata(
                membershipId,
                "finalPrice",
                finalPrice);

        String persistedPromotionId =
                jdbcTemplate.queryForObject(
                        """
                        select metadata ->> 'promotionId'
                        from gym.audit_entries
                        where resource_id = ?
                          and action_code = 'MEMBERSHIP_RENEWED'
                        """,
                        String.class,
                        membershipId);

        if (promotionId == null) {
            assertThat(persistedPromotionId)
                    .isNull();
        } else {
            assertThat(persistedPromotionId)
                    .isEqualTo(
                            promotionId.toString());
        }
    }

    private void assertAuditMetadata(
            UUID membershipId,
            String property,
            String expectedValue) {

        String sql =
                auditMetadataQuery(property);

        assertThat(
                jdbcTemplate.queryForObject(
                        sql,
                        String.class,
                        membershipId))
                .isEqualTo(expectedValue);
    }

    private static String auditMetadataQuery(
            String property) {

        return switch (property) {
            case "membershipPeriodId" ->
                    auditMetadataSql(
                            "membershipPeriodId");
            case "periodNumber" ->
                    auditMetadataSql(
                            "periodNumber");
            case "previousStatus" ->
                    auditMetadataSql(
                            "previousStatus");
            case "resultingStatus" ->
                    auditMetadataSql(
                            "resultingStatus");
            case "statusChanged" ->
                    auditMetadataSql(
                            "statusChanged");
            case "listPrice" ->
                    auditMetadataSql(
                            "listPrice");
            case "discountAmount" ->
                    auditMetadataSql(
                            "discountAmount");
            case "finalPrice" ->
                    auditMetadataSql(
                            "finalPrice");
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported audit metadata property: "
                                    + property);
        };
    }

    private static String auditMetadataSql(
            String property) {

        return """
                select metadata ->> '%s'
                from gym.audit_entries
                where resource_id = ?
                  and action_code = 'MEMBERSHIP_RENEWED'
                """
                .formatted(property);
    }

    static String renewalBody(
            UUID planId,
            UUID promotionId,
            String startsOn,
            long version) {

        String promotionValue =
                promotionId == null
                        ? "null"
                        : "\""
                          + promotionId
                          + "\"";

        String startsOnValue =
                startsOn == null
                        ? "null"
                        : "\""
                          + startsOn
                          + "\"";

        return """
                {
                  "membershipPlanId": "%s",
                  "promotionId": %s,
                  "startsOn": %s,
                  "version": %d
                }
                """
                .formatted(
                        planId,
                        promotionValue,
                        startsOnValue,
                        version);
    }
}