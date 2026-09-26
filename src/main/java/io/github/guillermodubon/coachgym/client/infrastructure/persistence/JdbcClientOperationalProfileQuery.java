package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import io.github.guillermodubon.coachgym.client.ClientAccessSummary;
import io.github.guillermodubon.coachgym.client.ClientEmergencyContactDetails;
import io.github.guillermodubon.coachgym.client.ClientMembershipSummary;
import io.github.guillermodubon.coachgym.client.ClientOperationalProfile;
import io.github.guillermodubon.coachgym.client.ClientPaymentSummary;
import io.github.guillermodubon.coachgym.client.ClientPhotoDetails;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.client.application.ClientOperationalProfileQuery;
import io.github.guillermodubon.coachgym.client.application.ClientProfileDataAccessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcClientOperationalProfileQuery implements ClientOperationalProfileQuery {

    static final String SQL = """
            select c.id, c.client_code, c.first_name, c.last_name, c.email,
                   c.phone, c.date_of_birth, c.status, c.created_at, c.updated_at,
                   c.version, c.home_branch_id,
                   ec.id as contact_id, ec.full_name as contact_name,
                   ec.relationship as contact_relationship,
                   ec.phone as contact_phone,
                   cm.id as membership_id, cm.membership_code,
                   cm.status as membership_status,
                   cp.plan_name_snapshot, cp.starts_on, cp.effective_ends_on,
                   pay.id as payment_id, pay.payment_code,
                   pay.status as payment_status, pay.amount as payment_amount,
                   pay.currency as payment_currency, pay.paid_at,
                   ar.id as access_id, ar.decision as access_decision,
                   ar.reason_code as access_reason_code, ar.occurred_at as access_occurred_at,
                   photo.id as photo_id, photo.content_type as photo_content_type,
                   photo.size_bytes as photo_size_bytes,
                   photo.updated_at as photo_updated_at,
                   photo.version as photo_version
            from gym.clients c
            left join lateral (
                select id, full_name, relationship, phone
                from gym.emergency_contacts
                where client_id = c.id
                order by is_primary desc, created_at asc, id asc
                limit 1
            ) ec on true
            left join lateral (
                select id, membership_code, status
                from gym.memberships
                where client_id = c.id
                order by
                    case status
                        when 'ACTIVE' then 0
                        when 'FROZEN' then 1
                        when 'EXPIRED' then 2
                        else 3
                    end,
                    updated_at desc,
                    id
                limit 1
            ) cm on true
            left join lateral (
                select id, plan_name_snapshot, starts_on, effective_ends_on
                from gym.membership_periods
                where membership_id = cm.id
                order by period_number desc
                limit 1
            ) cp on true
            left join lateral (
                select id, payment_code, status, amount, currency, paid_at
                from gym.payments
                where client_id = c.id
                  and membership_period_id = cp.id
                order by paid_at desc, id desc
                limit 1
            ) pay on true
            left join lateral (
            select id, decision, reason_code, occurred_at
            from gym.access_records
            where client_id = c.id
            order by occurred_at desc, id desc
            limit 1
            ) ar on true
            left join gym.client_photos photo on photo.client_id = c.id
            where c.id = :clientId
              and (cast(:branchId as uuid) is null
                   or c.home_branch_id = cast(:branchId as uuid))
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcClientOperationalProfileQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public Optional<ClientOperationalProfile> findById(UUID clientId) {
        return findById(clientId, null);
    }

    @Override
    public Optional<ClientOperationalProfile> findById(UUID clientId, UUID branchId) {
        Objects.requireNonNull(clientId, "Client id is required.");
        try {
            List<ClientOperationalProfile> rows = jdbcTemplate.query(
                    SQL,
                    new MapSqlParameterSource()
                            .addValue("clientId", clientId)
                            .addValue("branchId", branchId),
                    JdbcClientOperationalProfileQuery::mapProfile);
            return rows.stream().findFirst();
        } catch (DataAccessException exception) {
            throw new ClientProfileDataAccessException(
                    "Client operational profile could not be read.", exception);
        }
    }

    private static ClientOperationalProfile mapProfile(ResultSet rs, int row)
            throws SQLException {
        return new ClientOperationalProfile(
                rs.getObject("id", UUID.class),
                rs.getString("client_code"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getObject("date_of_birth", java.time.LocalDate.class),
                ClientStatus.valueOf(rs.getString("status")),
                contact(rs), membership(rs), payment(rs), access(rs), photo(rs),
                instant(rs, "created_at"), instant(rs, "updated_at"),
                rs.getLong("version"),
                rs.getObject("home_branch_id", UUID.class));
    }

    private static ClientEmergencyContactDetails contact(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("contact_id", UUID.class);
        return id == null ? null : new ClientEmergencyContactDetails(
                id, rs.getString("contact_name"),
                rs.getString("contact_relationship"), rs.getString("contact_phone"));
    }

    private static ClientMembershipSummary membership(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("membership_id", UUID.class);
        return id == null ? null : new ClientMembershipSummary(
                id, rs.getString("membership_code"),
                rs.getString("membership_status"), rs.getString("plan_name_snapshot"),
                rs.getObject("starts_on", java.time.LocalDate.class),
                rs.getObject("effective_ends_on", java.time.LocalDate.class));
    }

    private static ClientPaymentSummary payment(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("payment_id", UUID.class);
        return id == null ? null : new ClientPaymentSummary(
                id, rs.getString("payment_code"), rs.getString("payment_status"),
                rs.getBigDecimal("payment_amount"), rs.getString("payment_currency"),
                instant(rs, "paid_at"));
    }

    private static ClientAccessSummary access(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("access_id", UUID.class);
        if (id == null) return null;
        String reason = rs.getString("access_reason_code");
        if (reason == null) reason = "ACCESS_ALLOWED";
        return new ClientAccessSummary(
                id, rs.getString("access_decision"), reason,
                instant(rs, "access_occurred_at"));
    }

    private static ClientPhotoDetails photo(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("photo_id", UUID.class);
        return id == null ? null : new ClientPhotoDetails(
                id, rs.getString("photo_content_type"),
                rs.getLong("photo_size_bytes"), instant(rs, "photo_updated_at"),
                rs.getLong("photo_version"));
    }

    private static java.time.Instant instant(ResultSet rs, String column)
            throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
