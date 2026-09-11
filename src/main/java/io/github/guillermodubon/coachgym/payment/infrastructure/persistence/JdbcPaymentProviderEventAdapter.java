package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventReservation;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventDetails;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventProcessingResult;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventStore;
import io.github.guillermodubon.coachgym.payment.application.FinalizePaymentProviderEventCommand;
import io.github.guillermodubon.coachgym.payment.application.PersistPaymentProviderEventCommand;
import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcPaymentProviderEventAdapter implements PaymentProviderEventStore {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcPaymentProviderEventAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public PaymentProviderEventReservation reserve(PersistPaymentProviderEventCommand command) {
        if (command == null || command.id() == null || command.provider() == null
                || command.providerEventReference() == null || command.providerEventReference().isBlank()
                || command.eventType() == null || command.receivedAt() == null) {
            throw new IllegalArgumentException("Complete provider event persistence data is required.");
        }
        int inserted = jdbcTemplate.update("""
                insert into gym.processed_payment_provider_events
                    (id, provider, provider_event_reference, event_type, payment_attempt_id,
                     processing_result, received_at)
                values (:id, :provider, :reference, :eventType, :attemptId,
                        'PENDING', :receivedAt)
                on conflict (provider, provider_event_reference) do nothing
                """, new MapSqlParameterSource()
                .addValue("id", command.id())
                .addValue("provider", command.provider().name())
                .addValue("reference", command.providerEventReference().strip())
                .addValue("eventType", command.eventType().name())
                .addValue("attemptId", command.paymentAttemptId())
                .addValue("receivedAt", OffsetDateTime.ofInstant(command.receivedAt(), ZoneOffset.UTC)));
        return inserted == 1
                ? PaymentProviderEventReservation.RESERVED
                : PaymentProviderEventReservation.ALREADY_RESERVED;
    }

    @Override
    @Transactional
    public PaymentProviderEventDetails findForProcessing(
            PaymentProvider provider,
            String providerEventReference) {
        if (provider == null || providerEventReference == null
                || providerEventReference.isBlank()) {
            throw new IllegalArgumentException("Provider event identity is required.");
        }
        return jdbcTemplate.query("""
                select id, provider, provider_event_reference, event_type, payment_attempt_id,
                       processing_result, received_at, processed_at
                from gym.processed_payment_provider_events
                where provider = :provider and provider_event_reference = :reference
                for update
                """, new MapSqlParameterSource()
                .addValue("provider", provider.name())
                .addValue("reference", providerEventReference.strip()),
                JdbcPaymentProviderEventAdapter::map).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Provider event reservation was not found."));
    }

    @Override
    @Transactional
    public void finalizeProcessing(FinalizePaymentProviderEventCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Provider event finalization is required.");
        }
        int updated = jdbcTemplate.update("""
                update gym.processed_payment_provider_events
                set processing_result = :result,
                    processed_at = :processedAt
                where provider = :provider
                  and provider_event_reference = :reference
                  and processing_result = 'PENDING'
                """, new MapSqlParameterSource()
                .addValue("provider", command.provider().name())
                .addValue("reference", command.providerEventReference())
                .addValue("result", command.processingResult().name())
                .addValue("processedAt", offset(command.processedAt())));
        if (updated != 1) {
            throw new IllegalStateException("Provider event is no longer pending.");
        }
    }

    private static PaymentProviderEventDetails map(ResultSet resultSet, int row)
            throws SQLException {
        OffsetDateTime processedAt = resultSet.getObject("processed_at", OffsetDateTime.class);
        return new PaymentProviderEventDetails(
                resultSet.getObject("id", java.util.UUID.class),
                PaymentProvider.valueOf(resultSet.getString("provider")),
                resultSet.getString("provider_event_reference"),
                io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventType
                        .valueOf(resultSet.getString("event_type")),
                resultSet.getObject("payment_attempt_id", java.util.UUID.class),
                PaymentProviderEventProcessingResult.valueOf(
                        resultSet.getString("processing_result")),
                resultSet.getObject("received_at", OffsetDateTime.class).toInstant(),
                processedAt == null ? null : processedAt.toInstant());
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
