package io.github.guillermodubon.coachgym.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.notification.ComposedEmail;
import io.github.guillermodubon.coachgym.notification.EmailAttachment;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryAttemptDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryLifecycleEvent;
import io.github.guillermodubon.coachgym.notification.EmailDeliverySource;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryLifecyclePolicy;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;

class TransactionalEmailDeliveryApplicationServiceTest {

    private static final UUID PAYMENT_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000001");
    private static final UUID DELIVERY_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    private EmailDeliverySourceResolver sourceResolver;
    private EmailComposer composer;
    private EmailSender sender;
    private EmailDeliveryStore deliveryStore;
    private EmailDeliveryQuery deliveryQuery;
    private ApplicationEventPublisher eventPublisher;
    private TransactionalEmailDeliveryApplicationService service;
    private EmailDeliverySource source;
    private ComposedEmail composed;

    @BeforeEach
    void setUp() {
        sourceResolver = org.mockito.Mockito.mock(EmailDeliverySourceResolver.class);
        composer = org.mockito.Mockito.mock(EmailComposer.class);
        sender = org.mockito.Mockito.mock(EmailSender.class);
        deliveryStore = org.mockito.Mockito.mock(EmailDeliveryStore.class);
        deliveryQuery = org.mockito.Mockito.mock(EmailDeliveryQuery.class);
        eventPublisher = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
        when(deliveryStore.claimForAttempt(
                any(UUID.class), anyLong(), any(Instant.class), any(Instant.class)))
                .thenAnswer(invocation -> Optional.of(new EmailDeliveryClaim(
                        invocation.getArgument(0, UUID.class),
                        UUID.randomUUID(),
                        invocation.getArgument(1, Long.class),
                        invocation.getArgument(2, Instant.class),
                        invocation.getArgument(3, Instant.class))));
        doAnswer(invocation -> {
            EmailDeliveryAttemptDetails attempt = invocation.getArgument(0);
            EmailDeliveryStatus status = invocation.getArgument(1);
            EmailDeliveryFailureCode failureCode = invocation.getArgument(2);
            String failureMessage = invocation.getArgument(3);
            Instant attemptedAt = invocation.getArgument(4);
            Instant sentAt = invocation.getArgument(5);
            long expectedVersion = invocation.getArgument(6);
            deliveryStore.appendAttempt(attempt);
            return deliveryStore.finalizeAttempt(
                    attempt.deliveryId(), status, failureCode, failureMessage,
                    attemptedAt, sentAt, expectedVersion);
        }).when(deliveryStore).appendAttemptAndFinalize(
                any(EmailDeliveryAttemptDetails.class),
                any(EmailDeliveryStatus.class),
                any(), any(), any(Instant.class), any(), anyLong());
        doAnswer(invocation -> {
            EmailDeliveryAttemptDetails attempt = invocation.getArgument(0);
            EmailDeliveryStatus status = invocation.getArgument(1);
            EmailDeliveryFailureCode failureCode = invocation.getArgument(2);
            String failureMessage = invocation.getArgument(3);
            Instant attemptedAt = invocation.getArgument(4);
            Instant sentAt = invocation.getArgument(5);
            long expectedVersion = invocation.getArgument(6);
            deliveryStore.appendAttempt(attempt);
            return deliveryStore.finalizeAttempt(
                    attempt.deliveryId(), status, failureCode, failureMessage,
                    attemptedAt, sentAt, expectedVersion);
        }).when(deliveryStore).appendAttemptAndFinalize(
                any(EmailDeliveryAttemptDetails.class),
                any(EmailDeliveryStatus.class),
                any(), any(), any(Instant.class), any(), anyLong(), any(UUID.class));
        service = new TransactionalEmailDeliveryApplicationService(
                sourceResolver,
                composer,
                sender,
                deliveryStore,
                deliveryQuery,
                new EmailDeliveryLifecyclePolicy(2),
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));

        EmailAttachment attachment = new EmailAttachment(
                "payment-receipt-" + SOURCE_ID + ".pdf",
                "application/pdf",
                new byte[]{1, 2, 3});
        source = new EmailDeliverySource(
                EmailDeliveryType.PAYMENT_RECEIPT,
                SOURCE_ID,
                CLIENT_ID,
                "client@example.com",
                attachment);
        composed = new ComposedEmail(
                "v1",
                new EmailMessage(
                        source.recipient(),
                        "no-reply@coach-gym.local",
                        "Coach Gym",
                        null,
                        "Coach Gym payment receipt",
                        "Receipt",
                        "<p>Receipt</p>",
                        attachment));
    }

    @Test
    void persistsIntentSendsOnceFinalizesAndPublishesSafeLifecycleEvent() {
        when(sourceResolver.resolvePaymentReceiptForPayment(PAYMENT_ID)).thenReturn(source);
        when(composer.compose(source)).thenReturn(composed);
        when(deliveryQuery.findByIdempotencyKeyDigest(anyString())).thenReturn(Optional.empty());
        when(deliveryStore.createPending(any(EmailDeliveryDetails.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, EmailDeliveryDetails.class));
        when(sender.send(composed.message())).thenReturn(EmailSendResult.sent());
        when(deliveryStore.appendAttempt(any(EmailDeliveryAttemptDetails.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, EmailDeliveryAttemptDetails.class));
        when(deliveryStore.finalizeAttempt(
                any(UUID.class), eq(EmailDeliveryStatus.SENT), eq(null), eq(null),
                any(Instant.class), any(Instant.class), eq(0L)))
                .thenAnswer(invocation -> sent(invocation.getArgument(0, UUID.class)));

        EmailDeliveryDetails result = service.requestPaymentReceiptEmail(
                new RequestPaymentReceiptEmailCommand(PAYMENT_ID), actor());

        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(result.attemptCount()).isEqualTo(1);
        assertThat(result.version()).isEqualTo(1);
        verify(deliveryStore).createPending(any(EmailDeliveryDetails.class));
        verify(deliveryStore).appendAttempt(any(EmailDeliveryAttemptDetails.class));
        verify(deliveryStore).finalizeAttempt(
                any(UUID.class), eq(EmailDeliveryStatus.SENT), eq(null), eq(null),
                any(Instant.class), any(Instant.class), eq(0L));
        ArgumentCaptor<EmailDeliveryLifecycleEvent> eventCaptor =
                ArgumentCaptor.forClass(EmailDeliveryLifecycleEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .extracting(
                        EmailDeliveryLifecycleEvent::sourceResourceId,
                        EmailDeliveryLifecycleEvent::clientId,
                        EmailDeliveryLifecycleEvent::maskedRecipient,
                        EmailDeliveryLifecycleEvent::actorUserId,
                        EmailDeliveryLifecycleEvent::actorIdentifier,
                        EmailDeliveryLifecycleEvent::currentStatus)
                .containsExactly(
                        SOURCE_ID,
                        CLIENT_ID,
                        "c***@example.com",
                        ACTOR_ID,
                        "admin",
                        EmailDeliveryStatus.SENT);
    }

    @Test
    void repeatedCanonicalRequestReturnsExistingDeliveryWithoutSendingAgain() {
        EmailDeliveryDetails sent = sent(DELIVERY_ID);
        when(sourceResolver.resolvePaymentReceiptForPayment(PAYMENT_ID)).thenReturn(source);
        when(composer.compose(source)).thenReturn(composed);
        when(deliveryQuery.findByIdempotencyKeyDigest(anyString())).thenReturn(Optional.of(sent));

        EmailDeliveryDetails result = service.requestPaymentReceiptEmail(
                new RequestPaymentReceiptEmailCommand(PAYMENT_ID), actor());

        assertThat(result).isEqualTo(sent);
        verify(sender, never()).send(any(EmailMessage.class));
        verify(deliveryStore, never()).createPending(any(EmailDeliveryDetails.class));
    }

    @Test
    void failedInitialDeliveryIsPersistedAndRequiresExplicitRetry() {
        when(sourceResolver.resolvePaymentReceiptForPayment(PAYMENT_ID)).thenReturn(source);
        when(composer.compose(source)).thenReturn(composed);
        when(deliveryQuery.findByIdempotencyKeyDigest(anyString())).thenReturn(Optional.empty());
        when(deliveryStore.createPending(any(EmailDeliveryDetails.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, EmailDeliveryDetails.class));
        when(sender.send(composed.message())).thenReturn(EmailSendResult.failed(
                EmailDeliveryFailureCode.TRANSPORT_TIMEOUT, "SMTP delivery timed out."));
        when(deliveryStore.appendAttempt(any(EmailDeliveryAttemptDetails.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, EmailDeliveryAttemptDetails.class));
        when(deliveryStore.finalizeAttempt(
                any(UUID.class), eq(EmailDeliveryStatus.FAILED),
                eq(EmailDeliveryFailureCode.TRANSPORT_TIMEOUT),
                eq("SMTP delivery timed out."), any(Instant.class), eq(null), eq(0L)))
                .thenAnswer(invocation -> failed(invocation.getArgument(0, UUID.class)));

        EmailDeliveryDetails result = service.requestPaymentReceiptEmail(
                new RequestPaymentReceiptEmailCommand(PAYMENT_ID), actor());

        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(result.lastFailureCode())
                .isEqualTo(EmailDeliveryFailureCode.TRANSPORT_TIMEOUT);
        verify(sender).send(composed.message());
    }

    @Test
    void retryUsesPersistedRecipientAndVersionAndCanFinalizeSuccessfully() {
        EmailDeliveryDetails failed = failed(DELIVERY_ID);
        when(deliveryQuery.findById(DELIVERY_ID)).thenReturn(Optional.of(failed));
        when(sourceResolver.resolve(EmailDeliveryType.PAYMENT_RECEIPT, SOURCE_ID))
                .thenReturn(source);
        when(composer.compose(source)).thenReturn(composed);
        when(sender.send(composed.message())).thenReturn(EmailSendResult.sent());
        when(deliveryStore.appendAttempt(any(EmailDeliveryAttemptDetails.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, EmailDeliveryAttemptDetails.class));
        when(deliveryStore.finalizeAttempt(
                eq(DELIVERY_ID), eq(EmailDeliveryStatus.SENT), eq(null), eq(null),
                any(Instant.class), any(Instant.class), eq(1L)))
                .thenAnswer(invocation -> sent(DELIVERY_ID, 2));

        EmailDeliveryDetails result = service.retryEmailDelivery(
                new RetryEmailDeliveryCommand(DELIVERY_ID, 1), actor());

        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(result.attemptCount()).isEqualTo(2);
        verify(sender).send(composed.message());
        verify(eventPublisher).publishEvent(any(EmailDeliveryLifecycleEvent.class));
    }

    @Test
    void staleRetryVersionIsRejectedBeforeSourceResolutionOrTransport() {
        EmailDeliveryDetails failed = failed(DELIVERY_ID);
        when(deliveryQuery.findById(DELIVERY_ID)).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> service.retryEmailDelivery(
                new RetryEmailDeliveryCommand(DELIVERY_ID, 0), actor()))
                .isInstanceOf(EmailDeliveryVersionConflictException.class);

        verify(sourceResolver, never()).resolve(any(EmailDeliveryType.class), any(UUID.class));
        verify(sender, never()).send(any(EmailMessage.class));
    }

    @Test
    void disabledOrUnexpectedSenderFailureIsConvertedToSafePersistedFailure() {
        when(sourceResolver.resolvePaymentReceiptForPayment(PAYMENT_ID)).thenReturn(source);
        when(composer.compose(source)).thenReturn(composed);
        when(deliveryQuery.findByIdempotencyKeyDigest(anyString())).thenReturn(Optional.empty());
        when(deliveryStore.createPending(any(EmailDeliveryDetails.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, EmailDeliveryDetails.class));
        when(sender.send(composed.message())).thenThrow(new IllegalStateException("smtp secret"));
        when(deliveryStore.appendAttempt(any(EmailDeliveryAttemptDetails.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, EmailDeliveryAttemptDetails.class));
        when(deliveryStore.finalizeAttempt(
                any(UUID.class), eq(EmailDeliveryStatus.FAILED),
                eq(EmailDeliveryFailureCode.UNEXPECTED_FAILURE),
                eq("The email could not be delivered."), any(Instant.class), eq(null), eq(0L)))
                .thenAnswer(invocation -> failed(invocation.getArgument(0, UUID.class),
                        EmailDeliveryFailureCode.UNEXPECTED_FAILURE,
                        "The email could not be delivered."));

        EmailDeliveryDetails result = service.requestPaymentReceiptEmail(
                new RequestPaymentReceiptEmailCommand(PAYMENT_ID), actor());

        assertThat(result.lastFailureMessage()).isEqualTo("The email could not be delivered.");
        verify(sender).send(composed.message());
    }

    @Test
    void ambiguousTransportOutcomeIsPersistedAsUncertainFailureWithoutAutomaticRetry() {
        when(sourceResolver.resolvePaymentReceiptForPayment(PAYMENT_ID)).thenReturn(source);
        when(composer.compose(source)).thenReturn(composed);
        when(deliveryQuery.findByIdempotencyKeyDigest(anyString())).thenReturn(Optional.empty());
        when(deliveryStore.createPending(any(EmailDeliveryDetails.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, EmailDeliveryDetails.class));
        when(sender.send(composed.message())).thenReturn(
                EmailSendResult.ambiguous("The SMTP transport outcome could not be confirmed."));
        when(deliveryStore.appendAttempt(any(EmailDeliveryAttemptDetails.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, EmailDeliveryAttemptDetails.class));
        when(deliveryStore.finalizeAttempt(
                any(UUID.class), eq(EmailDeliveryStatus.FAILED),
                eq(EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME),
                eq("The SMTP transport outcome could not be confirmed."),
                any(Instant.class), eq(null), eq(0L)))
                .thenAnswer(invocation -> failed(invocation.getArgument(0, UUID.class),
                        EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME,
                        "The SMTP transport outcome could not be confirmed."));

        EmailDeliveryDetails result = service.requestPaymentReceiptEmail(
                new RequestPaymentReceiptEmailCommand(PAYMENT_ID), actor());

        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(result.lastFailureCode())
                .isEqualTo(EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME);
        ArgumentCaptor<EmailDeliveryLifecycleEvent> eventCaptor =
                ArgumentCaptor.forClass(EmailDeliveryLifecycleEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().attemptResult())
                .isEqualTo(io.github.guillermodubon.coachgym.notification.EmailAttemptResult.AMBIGUOUS);
    }

    @Test
    void concurrentIdenticalInitialRequestsSendAtMostOnceLocally() throws Exception {
        EmailDeliveryDetails winningDelivery = sent(DELIVERY_ID);
        when(sourceResolver.resolvePaymentReceiptForPayment(PAYMENT_ID)).thenReturn(source);
        when(composer.compose(source)).thenReturn(composed);
        AtomicInteger initialLookups = new AtomicInteger();
        when(deliveryQuery.findByIdempotencyKeyDigest(anyString())).thenAnswer(invocation ->
                initialLookups.getAndIncrement() == 0
                        ? Optional.empty() : Optional.of(winningDelivery));
        when(deliveryStore.createPending(any(EmailDeliveryDetails.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, EmailDeliveryDetails.class));
        CountDownLatch enteredSender = new CountDownLatch(1);
        AtomicInteger sendCalls = new AtomicInteger();
        when(sender.send(composed.message())).thenAnswer(invocation -> {
            sendCalls.incrementAndGet();
            enteredSender.countDown();
            Thread.sleep(100);
            return EmailSendResult.sent();
        });
        when(deliveryStore.appendAttempt(any(EmailDeliveryAttemptDetails.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, EmailDeliveryAttemptDetails.class));
        when(deliveryStore.finalizeAttempt(
                any(UUID.class), eq(EmailDeliveryStatus.SENT), eq(null), eq(null),
                any(Instant.class), any(Instant.class), eq(0L)))
                .thenAnswer(invocation -> sent(invocation.getArgument(0, UUID.class)));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<EmailDeliveryDetails> first = executor.submit(() ->
                    service.requestPaymentReceiptEmail(
                            new RequestPaymentReceiptEmailCommand(PAYMENT_ID), actor()));
            assertThat(enteredSender.await(2, TimeUnit.SECONDS)).isTrue();
            Future<EmailDeliveryDetails> second = executor.submit(() ->
                    service.requestPaymentReceiptEmail(
                            new RequestPaymentReceiptEmailCommand(PAYMENT_ID), actor()));

            assertThat(first.get(2, TimeUnit.SECONDS).status())
                    .isEqualTo(EmailDeliveryStatus.SENT);
            assertThat(second.get(2, TimeUnit.SECONDS).status())
                    .isEqualTo(EmailDeliveryStatus.SENT);
            assertThat(sendCalls).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentRetriesAllowOnlyOneSenderAndRejectTheStaleWinner() throws Exception {
        EmailDeliveryDetails failed = failed(DELIVERY_ID);
        AtomicInteger retryLookups = new AtomicInteger();
        when(deliveryQuery.findById(DELIVERY_ID)).thenAnswer(invocation ->
                retryLookups.getAndIncrement() == 0
                        ? Optional.of(failed) : Optional.of(sent(DELIVERY_ID)));
        when(sourceResolver.resolve(EmailDeliveryType.PAYMENT_RECEIPT, SOURCE_ID))
                .thenReturn(source);
        when(composer.compose(source)).thenReturn(composed);
        CountDownLatch enteredSender = new CountDownLatch(1);
        AtomicInteger sendCalls = new AtomicInteger();
        when(sender.send(composed.message())).thenAnswer(invocation -> {
            sendCalls.incrementAndGet();
            enteredSender.countDown();
            Thread.sleep(100);
            return EmailSendResult.sent();
        });
        when(deliveryStore.appendAttempt(any(EmailDeliveryAttemptDetails.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, EmailDeliveryAttemptDetails.class));
        when(deliveryStore.finalizeAttempt(
                eq(DELIVERY_ID), eq(EmailDeliveryStatus.SENT), eq(null), eq(null),
                any(Instant.class), any(Instant.class), eq(1L)))
                .thenAnswer(invocation -> sent(DELIVERY_ID, 2));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<EmailDeliveryDetails> first = executor.submit(() -> service.retryEmailDelivery(
                    new RetryEmailDeliveryCommand(DELIVERY_ID, 1), actor()));
            assertThat(enteredSender.await(2, TimeUnit.SECONDS)).isTrue();
            Future<EmailDeliveryDetails> second = executor.submit(() -> service.retryEmailDelivery(
                    new RetryEmailDeliveryCommand(DELIVERY_ID, 1), actor()));

            assertThat(first.get(2, TimeUnit.SECONDS).status())
                    .isEqualTo(EmailDeliveryStatus.SENT);
            assertThatThrownBy(() -> second.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(EmailDeliveryStateConflictException.class);
            assertThat(sendCalls).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private AuthenticatedActor actor() {
        return new AuthenticatedActor(ACTOR_ID, "admin");
    }

    private static EmailDeliveryDetails sent(UUID deliveryId) {
        return sent(deliveryId, 1);
    }

    private static EmailDeliveryDetails sent(UUID deliveryId, int attempts) {
        return new EmailDeliveryDetails(
                deliveryId,
                EmailDeliveryType.PAYMENT_RECEIPT,
                SOURCE_ID,
                CLIENT_ID,
                "client@example.com",
                "Coach Gym payment receipt",
                "v1",
                "PAYMENT_RECEIPT",
                SOURCE_ID,
                "payment-receipt-" + SOURCE_ID + ".pdf",
                "application/pdf",
                3,
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81",
                "a".repeat(64),
                EmailDeliveryStatus.SENT,
                attempts,
                null,
                null,
                NOW,
                ACTOR_ID,
                NOW,
                NOW,
                NOW,
                NOW,
                attempts);
    }

    private static EmailDeliveryDetails failed(UUID deliveryId) {
        return failed(deliveryId, EmailDeliveryFailureCode.TRANSPORT_TIMEOUT,
                "SMTP delivery timed out.");
    }

    private static EmailDeliveryDetails failed(
            UUID deliveryId,
            EmailDeliveryFailureCode code,
            String message) {
        return new EmailDeliveryDetails(
                deliveryId,
                EmailDeliveryType.PAYMENT_RECEIPT,
                SOURCE_ID,
                CLIENT_ID,
                "client@example.com",
                "Coach Gym payment receipt",
                "v1",
                "PAYMENT_RECEIPT",
                SOURCE_ID,
                "payment-receipt-" + SOURCE_ID + ".pdf",
                "application/pdf",
                3,
                "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81",
                "a".repeat(64),
                EmailDeliveryStatus.FAILED,
                1,
                code,
                message,
                NOW,
                ACTOR_ID,
                null,
                NOW,
                NOW,
                NOW,
                1);
    }
}
