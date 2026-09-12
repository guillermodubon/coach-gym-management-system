package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptCreated;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptFailureCode;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptProviderStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.PaymentProviderEventAcknowledged;
import io.github.guillermodubon.coachgym.payment.PaymentProviderPaymentConfirmed;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

class PaymentAttemptAuditIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-10T16:00:00Z");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    void persistsTheCompleteAttemptAuditTrailWithSafeMetadata() {
        UUID attemptId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID actorId = adminId;

        eventPublisher.publishEvent(new PaymentAttemptCreated(
                attemptId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                PaymentProvider.STRIPE, new BigDecimal("25.00"), "USD",
                actorId, "payment-admin", NOW));
        eventPublisher.publishEvent(new PaymentAttemptStatusChanged(
                attemptId, PaymentProvider.STRIPE, PaymentAttemptStatus.CREATED,
                PaymentAttemptStatus.PROCESSING, null, null, actorId, NOW));
        eventPublisher.publishEvent(new PaymentAttemptStatusChanged(
                attemptId, PaymentProvider.STRIPE, PaymentAttemptStatus.PROCESSING,
                PaymentAttemptStatus.CANCELLED,
                PaymentAttemptFailureCode.PROVIDER_CANCELLED, null, actorId, NOW));
        eventPublisher.publishEvent(new PaymentAttemptProviderStatusChanged(
                attemptId, PaymentProvider.STRIPE, PaymentAttemptStatus.PROCESSING,
                PaymentAttemptStatus.FAILED,
                PaymentAttemptFailureCode.PROVIDER_DECLINED, null, true, NOW));
        eventPublisher.publishEvent(new PaymentProviderEventAcknowledged(
                attemptId, PaymentProvider.STRIPE, "CHECKOUT_COMPLETED",
                "PROCESSED", true, NOW));
        eventPublisher.publishEvent(new PaymentProviderPaymentConfirmed(
                attemptId, paymentId, PaymentProvider.STRIPE,
                new BigDecimal("25.00"), "USD", NOW));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where resource_id = ?
                """, Integer.class, attemptId)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where resource_type = 'PAYMENT'
                  and resource_id = ?
                  and action_code = 'PAYMENT_PROVIDER_PAYMENT_CONFIRMED'
                """, Integer.class, paymentId)).isEqualTo(1);
        String metadata = jdbcTemplate.queryForObject("""
                select string_agg(metadata::text, ' ' order by occurred_at, id)
                from gym.audit_entries
                where resource_id = ?
                """, String.class, attemptId);
        assertThat(metadata).contains("STRIPE", "PROCESSED")
                .doesNotContain("checkoutUrl", "\"providerEventReference\":", "payload",
                        "signature", "secret", "cardNumber", "cs_test", "pi_test");
    }
}
