package io.github.guillermodubon.coachgym.membership;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

abstract class AbstractMembershipFreezeApiIntegrationTest
        extends AbstractMembershipApiIntegrationTest {

    protected UUID createActiveMembership(
            MockHttpSession session)
            throws Exception {

        String uniqueSuffix =
                UUID.randomUUID().toString();

        UUID clientId =
                createClient(
                        session,
                        "freeze-client-"
                                + uniqueSuffix
                                + "@example.com");

        UUID planId =
                createPlan(
                        session,
                        "Freeze Test Plan "
                                + uniqueSuffix,
                        "25.00",
                        "USD");

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/memberships")
                                        .session(session)
                                        .with(csrf())
                                        .contentType(
                                                "application/json")
                                        .content(
                                                membershipBody(
                                                        clientId,
                                                        planId,
                                                        null,
                                                        "2026-09-01")))
                        .andExpect(status().isCreated())
                        .andReturn();

        return responseId(result);
    }

    protected ResultActions freezeMembership(
            MockHttpSession session,
            UUID membershipId,
            String startsOn,
            String plannedEndsOn,
            String reason,
            long version) throws Exception {

        return mockMvc.perform(
                post(
                        "/api/v1/memberships/{id}/freeze",
                        membershipId)
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content(
                                freezeBody(
                                        startsOn,
                                        plannedEndsOn,
                                        reason,
                                        version)));
    }

    protected ResultActions reactivateMembership(
            MockHttpSession session,
            UUID membershipId,
            String reactivatedOn,
            long version) throws Exception {

        return mockMvc.perform(
                post(
                        "/api/v1/memberships/{id}/reactivate",
                        membershipId)
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content(
                                reactivationBody(
                                        reactivatedOn,
                                        version)));
    }

    protected String freezeBody(
            String startsOn,
            String plannedEndsOn,
            String reason,
            long version) {

        return """
                {
                  "startsOn": "%s",
                  "plannedEndsOn": "%s",
                  "reason": "%s",
                  "version": %d
                }
                """.formatted(
                startsOn,
                plannedEndsOn,
                reason,
                version);
    }

    protected String reactivationBody(
            String reactivatedOn,
            long version) {

        return """
                {
                  "reactivatedOn": "%s",
                  "version": %d
                }
                """.formatted(
                reactivatedOn,
                version);
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

        return version == null ? -1L : version;
    }

    protected int membershipPeriodCount(
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

        return count == null ? 0 : count;
    }

    protected UUID currentPeriodId(
            UUID membershipId) {

        return jdbcTemplate.queryForObject(
                """
                select id
                from gym.membership_periods
                where membership_id = ?
                order by period_number desc
                limit 1
                """,
                UUID.class,
                membershipId);
    }

    protected int openFreezeCount(
            UUID membershipId) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.membership_freezes
                        where membership_id = ?
                          and reactivated_on is null
                        """,
                        Integer.class,
                        membershipId);

        return count == null ? 0 : count;
    }

    protected int closedFreezeCount(
            UUID membershipId) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.membership_freezes
                        where membership_id = ?
                          and reactivated_on is not null
                        """,
                        Integer.class,
                        membershipId);

        return count == null ? 0 : count;
    }

    protected Map<String, Object> freezeRow(
            UUID membershipId) {

        return jdbcTemplate.queryForMap(
                """
                select
                    id,
                    membership_id,
                    membership_period_id,
                    starts_on,
                    planned_ends_on,
                    reason,
                    reactivated_on,
                    created_by_user_id,
                    reactivated_by_user_id,
                    version
                from gym.membership_freezes
                where membership_id = ?
                order by created_at desc
                limit 1
                """,
                membershipId);
    }

    protected int statusHistoryCount(
            UUID membershipId,
            String previousStatus,
            String newStatus) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.membership_status_history
                        where membership_id = ?
                          and previous_status = ?
                          and new_status = ?
                        """,
                        Integer.class,
                        membershipId,
                        previousStatus,
                        newStatus);

        return count == null ? 0 : count;
    }

    protected int membershipAuditCount(
            UUID membershipId,
            String actionCode) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.audit_entries
                        where resource_type = 'MEMBERSHIP'
                          and resource_id = ?
                          and action_code = ?
                        """,
                        Integer.class,
                        membershipId,
                        actionCode);

        return count == null ? 0 : count;
    }

    protected String auditActorIdentifier(
            UUID membershipId,
            String actionCode) {

        return jdbcTemplate.queryForObject(
                """
                select actor_identifier_snapshot
                from gym.audit_entries
                where resource_type = 'MEMBERSHIP'
                  and resource_id = ?
                  and action_code = ?
                order by occurred_at desc
                limit 1
                """,
                String.class,
                membershipId,
                actionCode);
    }

    protected String auditMetadataValue(
            UUID membershipId,
            String actionCode,
            String property) {

        return jdbcTemplate.queryForObject(
                """
                select jsonb_extract_path_text(
                        metadata,
                        cast(? as text)
                       )
                from gym.audit_entries
                where resource_type = 'MEMBERSHIP'
                  and resource_id = ?
                  and action_code = ?
                order by occurred_at desc
                limit 1
                """,
                String.class,
                property,
                membershipId,
                actionCode);
    }
}