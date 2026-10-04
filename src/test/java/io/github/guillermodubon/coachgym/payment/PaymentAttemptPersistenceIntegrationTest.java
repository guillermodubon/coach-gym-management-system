package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptStore;
import io.github.guillermodubon.coachgym.payment.application.CardCheckoutGateway;
import io.github.guillermodubon.coachgym.payment.application.CheckoutRedirectPolicy;
import io.github.guillermodubon.coachgym.payment.application.CancelPaymentAttemptPersistenceCommand;
import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptVersionConflictException;
import io.github.guillermodubon.coachgym.payment.application.FailPaymentAttemptCommand;
import io.github.guillermodubon.coachgym.payment.application.ProcessPaymentAttemptCommand;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventReservation;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventApplicationService;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventProcessingResult;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventStore;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventType;
import io.github.guillermodubon.coachgym.payment.application.PersistPaymentAttemptCommand;
import io.github.guillermodubon.coachgym.payment.application.PersistPaymentProviderEventCommand;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderWebhookLimits;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderWebhookVerifier;
import io.github.guillermodubon.coachgym.payment.application.VerifiedPaymentProviderEvent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "coach-gym.payment.stripe.enabled=true",
        "coach-gym.payment.stripe.sandbox=true",
        "coach-gym.payment.stripe.secret-key=sk_test_integration_only",
        "coach-gym.payment.stripe.webhook-signing-secret=whsec_integration_only",
        "coach-gym.payment.stripe.success-url=http://localhost:8080/stripe/success",
        "coach-gym.payment.stripe.cancel-url=http://localhost:8080/stripe/cancel"
})
class PaymentAttemptPersistenceIntegrationTest extends AbstractPaymentCorrectionApiIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-10T16:00:00Z");
    private static final String WEBHOOK_SECRET = "whsec_integration_only";

    @Autowired
    private PaymentAttemptStore paymentAttemptStore;

    @Autowired
    private PaymentProviderEventStore paymentProviderEventStore;

    @Autowired
    private PaymentProviderEventApplicationService paymentProviderEventApplicationService;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void enabledStripeRuntimeContainsOneVerifierAndItsHealthIndicator() {
        assertThat(applicationContext.getBeansOfType(PaymentProviderWebhookVerifier.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(CardCheckoutGateway.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(CheckoutRedirectPolicy.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(PaymentProviderWebhookLimits.class))
                .hasSize(1);
        assertThat(applicationContext.containsBean("stripe")).isTrue();
    }

    @Test
    void persistsCreatedAttemptWithInitialAppendOnlyHistory() throws Exception {
        PaymentFixture fixture = fixture();
        UUID attemptId = UUID.randomUUID();

        PaymentAttemptDetails attempt = paymentAttemptStore.create(command(attemptId, fixture));

        assertThat(attempt.status()).isEqualTo(PaymentAttemptStatus.CREATED);
        assertThat(attempt.currency()).isEqualTo("USD");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.payment_attempt_status_history
                where payment_attempt_id = ? and previous_status is null and new_status = 'CREATED'
                """, Integer.class, attemptId)).isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                update gym.payment_attempt_status_history set change_source = 'SYSTEM'
                where payment_attempt_id = ?
                """, attemptId)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void enforcesProviderReferenceUniquenessAndMatchingSucceededPayment() throws Exception {
        PaymentFixture fixture = fixture();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        paymentAttemptStore.create(command(first, fixture));
        paymentAttemptStore.create(command(second, fixture));

        jdbcTemplate.update("""
                update gym.payment_attempts set checkout_reference = 'checkout-unique', version = version + 1
                where id = ?
                """, first);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                update gym.payment_attempts set checkout_reference = 'checkout-unique', version = version + 1
                where id = ?
                """, second)).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                update gym.payment_attempts set status = 'SUCCEEDED', checkout_reference = 'checkout-card',
                    provider_payment_reference = 'provider-payment', confirmed_payment_id = ?,
                    completed_at = ?, version = version + 1
                where id = ?
                """, fixture.paymentId(), NOW, second)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void reservesProviderEventIdentityExactlyOnce() throws Exception {
        PaymentFixture fixture = fixture();
        UUID attemptId = UUID.randomUUID();
        paymentAttemptStore.create(command(attemptId, fixture));
        PersistPaymentProviderEventCommand event = new PersistPaymentProviderEventCommand(
                UUID.randomUUID(), PaymentProvider.STRIPE, "event-unique",
                PaymentProviderEventType.CHECKOUT_COMPLETED, attemptId, NOW);

        assertThat(paymentProviderEventStore.reserve(event))
                .isEqualTo(PaymentProviderEventReservation.RESERVED);
        assertThat(paymentProviderEventStore.reserve(new PersistPaymentProviderEventCommand(
                UUID.randomUUID(), PaymentProvider.STRIPE, "event-unique",
                PaymentProviderEventType.CHECKOUT_COMPLETED, attemptId, NOW)))
                .isEqualTo(PaymentProviderEventReservation.ALREADY_RESERVED);
    }

    @Test
    void persistsProcessingExpirationAndProviderConfirmedCancellation() throws Exception {
        PaymentFixture fixture = fixture();
        UUID attemptId = UUID.randomUUID();
        UUID actorId = actorId(fixture);
        paymentAttemptStore.create(command(attemptId, fixture));

        PaymentAttemptDetails processing = paymentAttemptStore.markProcessing(
                new ProcessPaymentAttemptCommand(
                        attemptId, 0, "cs_test_persistence",
                        NOW.plusSeconds(900), actorId, NOW.plusSeconds(1)));

        assertThat(processing.status()).isEqualTo(PaymentAttemptStatus.PROCESSING);
        assertThat(processing.version()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select checkout_expires_at > created_at from gym.payment_attempts where id = ?",
                Boolean.class, attemptId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.payment_attempt_status_history where payment_attempt_id = ?",
                Integer.class, attemptId)).isEqualTo(2);

        PaymentAttemptDetails cancelled = paymentAttemptStore.markCancelled(
                new CancelPaymentAttemptPersistenceCommand(
                        attemptId, 1, actorId, NOW.plusSeconds(2)));

        assertThat(cancelled.status()).isEqualTo(PaymentAttemptStatus.CANCELLED);
        assertThat(cancelled.failureCode())
                .isEqualTo(PaymentAttemptFailureCode.PROVIDER_CANCELLED);
        assertThat(cancelled.version()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.payment_attempt_status_history where payment_attempt_id = ?",
                Integer.class, attemptId)).isEqualTo(3);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                update gym.payment_attempts
                set checkout_expires_at = ?
                where id = ?
                """, NOW.plusSeconds(1), attemptId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void recordsProviderFailureAndRejectsStaleVersion() throws Exception {
        PaymentFixture fixture = fixture();
        UUID attemptId = UUID.randomUUID();
        UUID actorId = actorId(fixture);
        paymentAttemptStore.create(command(attemptId, fixture));

        PaymentAttemptDetails failed = paymentAttemptStore.markFailed(
                new FailPaymentAttemptCommand(
                        attemptId, 0, PaymentAttemptFailureCode.PROVIDER_UNAVAILABLE,
                        actorId, NOW.plusSeconds(1)));

        assertThat(failed.status()).isEqualTo(PaymentAttemptStatus.FAILED);
        assertThat(failed.version()).isEqualTo(1);
        assertThatThrownBy(() -> paymentAttemptStore.markFailed(
                new FailPaymentAttemptCommand(
                        attemptId, 0, PaymentAttemptFailureCode.PROVIDER_UNAVAILABLE,
                        actorId, NOW.plusSeconds(2))))
                .isInstanceOf(PaymentAttemptVersionConflictException.class);
    }

    @Test
    void appliesMatchingProviderSuccessExactlyOnce() throws Exception {
        PaymentFixture fixture = fixture();
        UUID attemptId = UUID.randomUUID();
        paymentAttemptStore.create(command(attemptId, fixture));
        paymentAttemptStore.markProcessing(new ProcessPaymentAttemptCommand(
                attemptId, 0, "cs_test_success", NOW.plusSeconds(900),
                actorId(fixture), NOW.plusSeconds(1)));

        VerifiedPaymentProviderEvent event = new VerifiedPaymentProviderEvent(
                PaymentProvider.STRIPE, "evt_success_once", PaymentProviderEventType.CHECKOUT_COMPLETED,
                attemptId, "cs_test_success", "pi_test_success",
                new BigDecimal("25.00"), "USD", NOW.plusSeconds(2));

        assertThat(paymentProviderEventApplicationService.process(event))
                .isEqualTo(PaymentProviderEventProcessingResult.PROCESSED);
        assertThat(paymentProviderEventApplicationService.process(event))
                .isEqualTo(PaymentProviderEventProcessingResult.PROCESSED);

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where resource_type = 'PAYMENT_ATTEMPT'
                  and resource_id = ?
                  and action_code = 'PAYMENT_PROVIDER_EVENT_DUPLICATE_ACKNOWLEDGED'
                """, Integer.class, attemptId)).isEqualTo(1);

        Map<String, Object> attemptRow = jdbcTemplate.queryForMap("""
                select status, provider_payment_reference, confirmed_payment_id, version
                from gym.payment_attempts where id = ?
                """, attemptId);
        assertThat(attemptRow.get("status")).isEqualTo("SUCCEEDED");
        assertThat(attemptRow.get("provider_payment_reference")).isEqualTo("pi_test_success");
        UUID paymentId = (UUID) attemptRow.get("confirmed_payment_id");
        assertThat(paymentId).isNotNull();
        assertThat(attemptRow.get("version")).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "select payment_method from gym.payments where id = ?", String.class, paymentId))
                .isEqualTo("CARD");
        Map<String, Object> confirmedPayment = jdbcTemplate.queryForMap("""
                select client_id, membership_id, membership_period_id, amount, currency,
                       external_reference, status
                from gym.payments where id = ?
                """, paymentId);
        assertThat(confirmedPayment.get("client_id")).isEqualTo(fixture.clientId());
        assertThat(confirmedPayment.get("membership_id")).isEqualTo(fixture.membershipId());
        assertThat(confirmedPayment.get("membership_period_id")).isEqualTo(fixture.periodId());
        assertThat(confirmedPayment.get("amount")).isEqualTo(new BigDecimal("25.00"));
        assertThat(confirmedPayment.get("currency")).isEqualTo("USD");
        assertThat(confirmedPayment.get("external_reference")).isNull();
        assertThat(confirmedPayment.get("status")).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.payments where id = ?", Integer.class, paymentId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.payment_attempt_status_history where payment_attempt_id = ?
                """, Integer.class, attemptId)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                select processing_result from gym.processed_payment_provider_events
                where provider_event_reference = 'evt_success_once'
                """, String.class)).isEqualTo("PROCESSED");
    }

    @Test
    void rejectsProviderAmountMismatchWithoutPartialWrites() throws Exception {
        PaymentFixture fixture = fixture();
        UUID attemptId = UUID.randomUUID();
        paymentAttemptStore.create(command(attemptId, fixture));
        paymentAttemptStore.markProcessing(new ProcessPaymentAttemptCommand(
                attemptId, 0, "cs_test_mismatch", NOW.plusSeconds(900),
                actorId(fixture), NOW.plusSeconds(1)));
        int paymentsBefore = jdbcTemplate.queryForObject(
                "select count(*) from gym.payments", Integer.class);

        VerifiedPaymentProviderEvent event = new VerifiedPaymentProviderEvent(
                PaymentProvider.STRIPE, "evt_mismatch_persisted", PaymentProviderEventType.CHECKOUT_COMPLETED,
                attemptId, "cs_test_mismatch", "pi_test_mismatch",
                new BigDecimal("99.00"), "USD", NOW.plusSeconds(2));

        assertThat(paymentProviderEventApplicationService.process(event))
                .isEqualTo(PaymentProviderEventProcessingResult.REJECTED);
        assertThat(jdbcTemplate.queryForObject(
                "select status from gym.payment_attempts where id = ?", String.class, attemptId))
                .isEqualTo("PROCESSING");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.payments", Integer.class)).isEqualTo(paymentsBefore);
        assertThat(jdbcTemplate.queryForObject("""
                select processing_result from gym.processed_payment_provider_events
                where provider_event_reference = 'evt_mismatch_persisted'
                """, String.class)).isEqualTo("REJECTED");
    }

    @Test
    void concurrentDuplicateProviderEventsCreateOnlyOnePayment() throws Exception {
        PaymentFixture fixture = fixture();
        UUID attemptId = UUID.randomUUID();
        paymentAttemptStore.create(command(attemptId, fixture));
        paymentAttemptStore.markProcessing(new ProcessPaymentAttemptCommand(
                attemptId, 0, "cs_test_concurrent", NOW.plusSeconds(900),
                actorId(fixture), NOW.plusSeconds(1)));

        VerifiedPaymentProviderEvent event = new VerifiedPaymentProviderEvent(
                PaymentProvider.STRIPE, "evt_concurrent_once", PaymentProviderEventType.CHECKOUT_COMPLETED,
                attemptId, "cs_test_concurrent", "pi_test_concurrent",
                new BigDecimal("25.00"), "USD", NOW.plusSeconds(2));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PaymentProviderEventProcessingResult> first = executor.submit(
                    () -> paymentProviderEventApplicationService.process(event));
            Future<PaymentProviderEventProcessingResult> second = executor.submit(
                    () -> paymentProviderEventApplicationService.process(event));

            assertThat(first.get()).isEqualTo(PaymentProviderEventProcessingResult.PROCESSED);
            assertThat(second.get()).isEqualTo(PaymentProviderEventProcessingResult.PROCESSED);
        } finally {
            executor.shutdownNow();
        }

        UUID paymentId = jdbcTemplate.queryForObject(
                "select confirmed_payment_id from gym.payment_attempts where id = ?",
                UUID.class, attemptId);
        assertThat(paymentId).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.payments where id = ?", Integer.class, paymentId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.payment_attempt_status_history where payment_attempt_id = ?
                """, Integer.class, attemptId)).isEqualTo(3);
    }

    @Test
    void providerFailureFinalizesAttemptWithoutCreatingPayment() throws Exception {
        PaymentFixture fixture = fixture();
        UUID attemptId = UUID.randomUUID();
        paymentAttemptStore.create(command(attemptId, fixture));
        paymentAttemptStore.markProcessing(new ProcessPaymentAttemptCommand(
                attemptId, 0, "cs_test_failed", NOW.plusSeconds(900),
                actorId(fixture), NOW.plusSeconds(1)));
        int paymentsBefore = jdbcTemplate.queryForObject(
                "select count(*) from gym.payments", Integer.class);

        VerifiedPaymentProviderEvent event = new VerifiedPaymentProviderEvent(
                PaymentProvider.STRIPE, "evt_failed_persisted", PaymentProviderEventType.PAYMENT_FAILED,
                attemptId, "cs_test_failed", null, null, null, NOW.plusSeconds(2));

        assertThat(paymentProviderEventApplicationService.process(event))
                .isEqualTo(PaymentProviderEventProcessingResult.PROCESSED);
        assertThat(jdbcTemplate.queryForObject(
                "select status from gym.payment_attempts where id = ?", String.class, attemptId))
                .isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "select failure_code from gym.payment_attempts where id = ?", String.class, attemptId))
                .isEqualTo("PROVIDER_DECLINED");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.payments", Integer.class)).isEqualTo(paymentsBefore);
    }

    @Test
    void signedCheckoutCompletionIsProcessedAndRepeatedDeliveryIsAcknowledgedIdempotently()
            throws Exception {
        PaymentFixture fixture = fixture();
        UUID attemptId = createProcessingAttempt(fixture, "cs_test_webhook_completed");
        long timestamp = Instant.now().getEpochSecond();
        String payload = completedPayload(
                "evt_webhook_completed", "cs_test_webhook_completed", "pi_test_webhook_completed",
                attemptId, 2_500, "usd", timestamp);

        postWebhook(payload, sign(payload, timestamp))
                .andExpect(status().isNoContent());
        postWebhook(payload, sign(payload, timestamp))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "select status from gym.payment_attempts where id = ?", String.class, attemptId))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.processed_payment_provider_events
                where provider_event_reference = 'evt_webhook_completed'
                  and processing_result = 'PROCESSED'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.payments where payment_method = 'CARD' and status = 'PAID'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void signedExpiredCheckoutAndPaymentIntentFailureFinalizeTheirAttempts()
            throws Exception {
        PaymentFixture expiredFixture = fixture();
        UUID expiredAttemptId = createProcessingAttempt(expiredFixture, "cs_test_webhook_expired");
        long expiredTimestamp = Instant.now().getEpochSecond();
        String expiredPayload = expiredPayload(
                "evt_webhook_expired", "cs_test_webhook_expired", expiredAttemptId, expiredTimestamp);

        postWebhook(expiredPayload, sign(expiredPayload, expiredTimestamp))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "select status from gym.payment_attempts where id = ?", String.class, expiredAttemptId))
                .isEqualTo("EXPIRED");

        PaymentFixture failedFixture = fixture();
        UUID failedAttemptId = createProcessingAttempt(failedFixture, "cs_test_webhook_failed");
        long failedTimestamp = Instant.now().getEpochSecond();
        String failedPayload = paymentIntentFailedPayload(
                "evt_webhook_failed", "pi_test_webhook_failed", failedAttemptId, failedTimestamp);

        postWebhook(failedPayload, sign(failedPayload, failedTimestamp))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "select status from gym.payment_attempts where id = ?", String.class, failedAttemptId))
                .isEqualTo("FAILED");
    }

    @Test
    void signedAmountAndCurrencyMismatchesAreRejectedWithoutConfirmingPayment()
            throws Exception {
        assertCompletedEventRejected("evt_webhook_amount_mismatch", 9_900, "usd");
        assertCompletedEventRejected("evt_webhook_currency_mismatch", 2_500, "eur");
    }

    @Test
    void invalidSignatureAndMissingAttemptMetadataAreRejectedBeforePaymentWrites()
            throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        UUID attemptId = UUID.randomUUID();
        String missingMetadataPayload = """
                {"id":"evt_webhook_missing_metadata","object":"event","api_version":"2024-06-20",
                "created":%d,"data":{"object":{"id":"cs_test_missing_metadata",
                "object":"checkout.session","metadata":{}}},"livemode":false,
                "pending_webhooks":1,"type":"checkout.session.expired"}
                """.formatted(timestamp).replaceAll("\\s+", "");
        String unknownAttemptPayload = expiredPayload(
                "evt_webhook_unknown_attempt", "cs_test_unknown_attempt", attemptId, timestamp);

        postWebhook(missingMetadataPayload, sign(missingMetadataPayload, timestamp))
                .andExpect(status().isBadRequest());
        postWebhook(unknownAttemptPayload, sign(unknownAttemptPayload, timestamp))
                .andExpect(status().isBadRequest());
        postWebhook(missingMetadataPayload, "t=" + timestamp + ",v1=invalid")
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.processed_payment_provider_events
                where provider_event_reference in (
                    'evt_webhook_missing_metadata', 'evt_webhook_unknown_attempt')
                """, Integer.class)).isZero();
    }

    private PaymentFixture fixture() throws Exception {
        var session = loginAsAdmin();
        return createPaidPayment(session, "CASH", null);
    }

    private PersistPaymentAttemptCommand command(UUID attemptId, PaymentFixture fixture) {
        return new PersistPaymentAttemptCommand(
                attemptId, fixture.clientId(), fixture.membershipId(), fixture.periodId(),
                PaymentProvider.STRIPE, new BigDecimal("25.00"), "USD", actorId(fixture), NOW);
    }

    private UUID actorId(PaymentFixture fixture) {
        return jdbcTemplate.queryForObject(
                "select registered_by_user_id from gym.payments where id = ?",
                UUID.class, fixture.paymentId());
    }

    private UUID createProcessingAttempt(PaymentFixture fixture, String checkoutReference) {
        UUID attemptId = UUID.randomUUID();
        paymentAttemptStore.create(command(attemptId, fixture));
        paymentAttemptStore.markProcessing(new ProcessPaymentAttemptCommand(
                attemptId, 0, checkoutReference, Instant.now().plusSeconds(900),
                actorId(fixture), Instant.now()));
        return attemptId;
    }

    private void assertCompletedEventRejected(String eventReference, long amountMinor, String currency)
            throws Exception {
        PaymentFixture fixture = fixture();
        String checkoutReference = "cs_test_" + eventReference;
        UUID attemptId = createProcessingAttempt(fixture, checkoutReference);
        long timestamp = Instant.now().getEpochSecond();
        String payload = completedPayload(
                eventReference, checkoutReference, "pi_test_" + eventReference,
                attemptId, amountMinor, currency, timestamp);
        int cardPaymentsBefore = jdbcTemplate.queryForObject(
                "select count(*) from gym.payments where payment_method = 'CARD'",
                Integer.class);

        postWebhook(payload, sign(payload, timestamp))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject(
                "select status from gym.payment_attempts where id = ?", String.class, attemptId))
                .isEqualTo("PROCESSING");
        assertThat(jdbcTemplate.queryForObject("""
                select processing_result from gym.processed_payment_provider_events
                where provider_event_reference = ?
                """, String.class, eventReference)).isEqualTo("REJECTED");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.payments where payment_method = 'CARD'",
                Integer.class)).isEqualTo(cardPaymentsBefore);
    }

    private org.springframework.test.web.servlet.ResultActions postWebhook(
            String payload, String signature) throws Exception {
        return mockMvc.perform(post("/api/v1/payment-provider/stripe/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature)
                .content(payload));
    }

    private static String completedPayload(
            String eventReference,
            String checkoutReference,
            String paymentReference,
            UUID attemptId,
            long amountMinor,
            String currency,
            long timestamp) {
        return """
                {"id":"%s","object":"event","api_version":"2024-06-20","created":%d,
                "data":{"object":{"id":"%s","object":"checkout.session","amount_total":%d,
                "currency":"%s","payment_intent":"%s","metadata":{"payment_attempt_id":"%s"}}},
                "livemode":false,"pending_webhooks":1,"type":"checkout.session.completed"}
                """.formatted(eventReference, timestamp, checkoutReference, amountMinor,
                        currency, paymentReference, attemptId)
                .replaceAll("\\s+", "");
    }

    private static String expiredPayload(
            String eventReference, String checkoutReference, UUID attemptId, long timestamp) {
        return """
                {"id":"%s","object":"event","api_version":"2024-06-20","created":%d,
                "data":{"object":{"id":"%s","object":"checkout.session",
                "metadata":{"payment_attempt_id":"%s"}}},"livemode":false,
                "pending_webhooks":1,"type":"checkout.session.expired"}
                """.formatted(eventReference, timestamp, checkoutReference, attemptId)
                .replaceAll("\\s+", "");
    }

    private static String paymentIntentFailedPayload(
            String eventReference, String paymentReference, UUID attemptId, long timestamp) {
        return """
                {"id":"%s","object":"event","api_version":"2024-06-20","created":%d,
                "data":{"object":{"id":"%s","object":"payment_intent",
                "metadata":{"payment_attempt_id":"%s"}}},"livemode":false,
                "pending_webhooks":1,"type":"payment_intent.payment_failed"}
                """.formatted(eventReference, timestamp, paymentReference, attemptId)
                .replaceAll("\\s+", "");
    }

    private static String sign(String payload, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new AssertionError("Could not sign the local Stripe test event.", exception);
        }
    }
}
