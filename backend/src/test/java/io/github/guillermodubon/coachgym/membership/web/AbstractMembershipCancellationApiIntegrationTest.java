package io.github.guillermodubon.coachgym.membership.web;

import static org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.post;



import java.time.LocalDate;
import java.util.UUID;

import io.github.guillermodubon.coachgym.membership.AbstractMembershipFreezeApiIntegrationTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;


abstract class AbstractMembershipCancellationApiIntegrationTest
        extends AbstractMembershipFreezeApiIntegrationTest {

    protected static final LocalDate CANCELLATION_DATE =
            LocalDate.of(
                    2026,
                    8,
                    24);

    protected static final LocalDate CANCELLATION_PERIOD_START =
            LocalDate.of(
                    2026,
                    8,
                    1);

    protected static final String CANCELLATION_REASON =
            "Client requested cancellation";


    protected ResultActions cancelMembership(
            UUID membershipId,
            long version,
            MockHttpSession session)
            throws Exception {

        return mockMvc.perform(
                post(
                        "/api/v1/memberships/{id}/cancel",
                        membershipId)
                        .session(session)
                        .with(csrf())
                        .contentType(
                                "application/json")
                        .content(
                                cancellationBody(
                                        CANCELLATION_DATE,
                                        CANCELLATION_REASON,
                                        version)));
    }

    protected ResultActions cancelMembershipWithoutCsrf(
            UUID membershipId,
            long version,
            MockHttpSession session)
            throws Exception {

        return mockMvc.perform(
                post(
                        "/api/v1/memberships/{id}/cancel",
                        membershipId)
                        .session(session)
                        .contentType(
                                "application/json")
                        .content(
                                cancellationBody(
                                        CANCELLATION_DATE,
                                        CANCELLATION_REASON,
                                        version)));
    }

    protected ResultActions cancelMembershipUnauthenticated(
            UUID membershipId,
            long version)
            throws Exception {

        return mockMvc.perform(
                post(
                        "/api/v1/memberships/{id}/cancel",
                        membershipId)
                        .with(csrf())
                        .contentType(
                                "application/json")
                        .content(
                                cancellationBody(
                                        CANCELLATION_DATE,
                                        CANCELLATION_REASON,
                                        version)));
    }

    protected ResultActions cancelMembershipWithBody(
            UUID membershipId,
            String body,
            MockHttpSession session)
            throws Exception {

        return mockMvc.perform(
                post(
                        "/api/v1/memberships/{id}/cancel",
                        membershipId)
                        .session(session)
                        .with(csrf())
                        .contentType(
                                "application/json")
                        .content(body));
    }

    protected String cancellationBody(
            LocalDate cancelledOn,
            String reason,
            long version) {

        return """
                {
                  "cancelledOn": "%s",
                  "reason": "%s",
                  "version": %d
                }
                """
                .formatted(
                        cancelledOn,
                        reason,
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

        return version == null
                ? 0L
                : version;
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

        return count == null
                ? 0
                : count;
    }

    protected boolean membershipHasCancelledAt(
            UUID membershipId) {

        Boolean result =
                jdbcTemplate.queryForObject(
                        """
                                select cancelled_at is not null
                                from gym.memberships
                                where id = ?
                                """,
                        Boolean.class,
                        membershipId);

        return Boolean.TRUE.equals(result);
    }

    protected UUID membershipCancelledByUserId(
            UUID membershipId) {

        return jdbcTemplate.queryForObject(
                """
                        select cancelled_by_user_id
                        from gym.memberships
                        where id = ?
                        """,
                UUID.class,
                membershipId);
    }

    protected String membershipCancellationReason(
            UUID membershipId) {

        return jdbcTemplate.queryForObject(
                """
                        select cancellation_reason
                        from gym.memberships
                        where id = ?
                        """,
                String.class,
                membershipId);
    }

    protected int cancellationStatusHistoryCount(
            UUID membershipId) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.membership_status_history
                        where membership_id = ?
                          and new_status = 'CANCELLED'
                        """,
                        Integer.class,
                        membershipId);

        return count == null
                ? 0
                : count;
    }

    protected String cancellationPreviousStatus(
            UUID membershipId) {

        return jdbcTemplate.queryForObject(
                """
                        select previous_status
                        from gym.membership_status_history
                        where membership_id = ?
                          and new_status = 'CANCELLED'
                        order by occurred_at desc
                        limit 1
                        """,
                String.class,
                membershipId);
    }

    protected int cancellationAuditCount(
            UUID membershipId) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                                select count(*)
                                from gym.audit_entries
                                where resource_type = 'MEMBERSHIP'
                                  and resource_id = ?
                                  and action_code = 'MEMBERSHIP_CANCELLED'
                                """,
                        Integer.class,
                        membershipId);

        return count == null
                ? 0
                : count;
    }

    protected String cancellationAuditActor(
            UUID membershipId) {

        return jdbcTemplate.queryForObject(
                """
                        select actor_identifier_snapshot
                        from gym.audit_entries
                        where resource_type = 'MEMBERSHIP'
                          and resource_id = ?
                          and action_code = 'MEMBERSHIP_CANCELLED'
                        order by occurred_at desc
                        limit 1
                        """,
                String.class,
                membershipId);
    }

    protected String cancellationAuditMetadata(
            UUID membershipId,
            String key) {

        return jdbcTemplate.queryForObject(
                """
                        select jsonb_extract_path_text(
                                metadata,
                                cast(? as text)
                               )
                        from gym.audit_entries
                        where resource_type = 'MEMBERSHIP'
                          and resource_id = ?
                          and action_code = 'MEMBERSHIP_CANCELLED'
                        order by occurred_at desc
                        limit 1
                        """,
                String.class,
                key,
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
                                  and cancelled_on is null
                                """,
                        Integer.class,
                        membershipId);

        return count == null
                ? 0
                : count;
    }

    protected int cancellationClosedFreezeCount(
            UUID membershipId) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                                select count(*)
                                from gym.membership_freezes
                                where membership_id = ?
                                  and cancelled_on is not null
                                  and cancelled_by_user_id is not null
                                """,
                        Integer.class,
                        membershipId);

        return count == null
                ? 0
                : count;
    }

    protected LocalDate freezeCancelledOn(
            UUID membershipId) {

        return jdbcTemplate.queryForObject(
                """
                select cancelled_on
                from gym.membership_freezes
                where membership_id = ?
                  and cancelled_on is not null
                order by updated_at desc
                limit 1
                """,
                LocalDate.class,
                membershipId);
    }

    protected UUID freezeCancelledByUserId(UUID membershipId) {
        return jdbcTemplate.queryForObject( """ 
        select cancelled_by_user_id 
        from gym.membership_freezes 
        where membership_id = ? 
        and cancelled_on is not null 
        order by updated_at desc 
        limit 1 
        """,
                UUID.class,
                membershipId
        );
    }

    protected void markMembershipExpired(UUID membershipId) {
        jdbcTemplate.update( """ 
        update gym.memberships 
        set status = 'EXPIRED', 
            updated_at = current_timestamp, 
            version = version + 1 
        where id = ? 
        """,
                membershipId
        );
    }

    protected UUID createCancellableActiveMembership(
            MockHttpSession session)
            throws Exception {

        UUID membershipId =
                super.createActiveMembership(
                        session);

        int updatedRows =
                jdbcTemplate.update(
                        """
                        update gym.membership_periods
                        set starts_on = ?
                        where membership_id = ?
                        """,
                        CANCELLATION_PERIOD_START,
                        membershipId);

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Expected to update exactly one membership period "
                            + "for membership "
                            + membershipId
                            + ", but updated "
                            + updatedRows
                            + ".");
        }

        LocalDate persistedStartsOn =
                jdbcTemplate.queryForObject(
                        """
                        select starts_on
                        from gym.membership_periods
                        where membership_id = ?
                        order by period_number desc
                        limit 1
                        """,
                        LocalDate.class,
                        membershipId);

        if (!CANCELLATION_PERIOD_START.equals(
                persistedStartsOn)) {

            throw new IllegalStateException(
                    "The membership period start date was not updated. "
                            + "Expected "
                            + CANCELLATION_PERIOD_START
                            + " but found "
                            + persistedStartsOn
                            + ".");
        }

        return membershipId;
    }

    protected LocalDate currentPeriodStartsOn(
            UUID membershipId) {

        return jdbcTemplate.queryForObject(
                """
                select starts_on
                from gym.membership_periods
                where membership_id = ?
                order by period_number desc
                limit 1
                """,
                LocalDate.class,
                membershipId);
    }

}