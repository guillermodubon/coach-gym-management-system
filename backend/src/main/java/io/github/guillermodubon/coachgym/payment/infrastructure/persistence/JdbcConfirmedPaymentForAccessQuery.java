package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.payment.ConfirmedPaymentForAccessQuery;
import io.github.guillermodubon.coachgym.payment.application.ConfirmedPaymentForAccessDataAccessException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment-owned read adapter for the access payment requirement.
 *
 * <p>The adapter deliberately returns only an existence result. Payment
 * details, attempts, provider records, and receipts remain inside the payment
 * module and are not part of the access contract.</p>
 */
@Repository
class JdbcConfirmedPaymentForAccessQuery
        implements ConfirmedPaymentForAccessQuery {

    static final String SQL = """
            select exists (
                select 1
                from gym.payments p
                where p.client_id = :clientId
                  and p.membership_id = :membershipId
                  and p.membership_period_id = :membershipPeriodId
                  and p.status = 'PAID'
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcConfirmedPaymentForAccessQuery(
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasConfirmedPaymentForPeriod(
            UUID clientId,
            UUID membershipId,
            UUID membershipPeriodId) {

        requireIdentifier(clientId, "Client id is required.");
        requireIdentifier(membershipId, "Membership id is required.");
        requireIdentifier(membershipPeriodId, "Membership period id is required.");

        MapSqlParameterSource parameters = new MapSqlParameterSource(
                Map.of(
                        "clientId", clientId,
                        "membershipId", membershipId,
                        "membershipPeriodId", membershipPeriodId));
        try {
            Boolean result = jdbcTemplate.queryForObject(
                    SQL, parameters, Boolean.class);
            if (result == null) {
                throw new ConfirmedPaymentForAccessDataAccessException(
                        "Confirmed payment for access could not be read.",
                        null);
            }
            return result;
        } catch (ConfirmedPaymentForAccessDataAccessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new ConfirmedPaymentForAccessDataAccessException(
                    "Confirmed payment for access could not be read.",
                    exception);
        }
    }

    private static void requireIdentifier(UUID value, String message) {
        Objects.requireNonNull(value, message);
    }
}
