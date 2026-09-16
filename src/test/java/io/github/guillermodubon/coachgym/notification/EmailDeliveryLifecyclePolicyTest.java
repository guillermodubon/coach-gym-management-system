package io.github.guillermodubon.coachgym.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryRetryLimitExceededException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryStateConflictException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryVersionConflictException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryQuery;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryStore;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import io.github.guillermodubon.coachgym.notification.application.RequestAccessCredentialEmailCommand;
import io.github.guillermodubon.coachgym.notification.application.RequestPaymentReceiptEmailCommand;
import io.github.guillermodubon.coachgym.notification.application.RetryEmailDeliveryCommand;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryLifecyclePolicy;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmailDeliveryLifecyclePolicyTest {

    private static final EmailDeliveryLifecyclePolicy POLICY =
            new EmailDeliveryLifecyclePolicy();
    private static final UUID ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    @Test
    void allowsOnlyDurableForwardLifecycleAndFailedRetryTransitions() {
        assertThat(POLICY.canTransition(EmailDeliveryStatus.PENDING, EmailDeliveryStatus.SENT))
                .isTrue();
        assertThat(POLICY.canTransition(EmailDeliveryStatus.PENDING, EmailDeliveryStatus.FAILED))
                .isTrue();
        assertThat(POLICY.canTransition(EmailDeliveryStatus.FAILED, EmailDeliveryStatus.SENT))
                .isTrue();
        assertThat(POLICY.canTransition(EmailDeliveryStatus.FAILED, EmailDeliveryStatus.FAILED))
                .isTrue();
        assertThat(POLICY.canTransition(EmailDeliveryStatus.SENT, EmailDeliveryStatus.FAILED))
                .isFalse();
    }

    @Test
    void onlyFailedDeliveryWithCurrentVersionAndRemainingRetriesIsEligible() {
        EmailDeliveryDetails failed = delivery(
                EmailDeliveryStatus.FAILED,
                1,
                4,
                EmailDeliveryFailureCode.TRANSPORT_TIMEOUT,
                "Transport timed out.");

        POLICY.requireRetryAllowed(failed, 4, 3);

        assertThatThrownBy(() -> POLICY.requireRetryAllowed(failed, 3, 3))
                .isInstanceOf(EmailDeliveryVersionConflictException.class);

        EmailDeliveryDetails sent = delivery(EmailDeliveryStatus.SENT, 1, 4, null, null);
        assertThatThrownBy(() -> POLICY.requireRetryAllowed(sent, 4, 3))
                .isInstanceOf(EmailDeliveryStateConflictException.class);

        EmailDeliveryDetails exhausted = delivery(
                EmailDeliveryStatus.FAILED,
                4,
                4,
                EmailDeliveryFailureCode.TRANSPORT_TIMEOUT,
                "Transport timed out.");
        assertThatThrownBy(() -> POLICY.requireRetryAllowed(exhausted, 4, 3))
                .isInstanceOf(EmailDeliveryRetryLimitExceededException.class);
    }

    @Test
    void retryLimitCountsRetriesAfterTheInitialAttempt() {
        EmailDeliveryDetails initialFailure = delivery(
                EmailDeliveryStatus.FAILED,
                1,
                0,
                EmailDeliveryFailureCode.TRANSPORT_REJECTED,
                "Transport rejected the message.");

        POLICY.requireRetryAllowed(initialFailure, 0, 1);
    }

    @Test
    void stalePendingDetectionNeverAuthorizesAnAutomaticResend() {
        EmailDeliveryLifecyclePolicy policy = new EmailDeliveryLifecyclePolicy(
                3, Duration.ofMinutes(15));
        EmailDeliveryDetails pending = delivery(
                EmailDeliveryStatus.PENDING, 0, 0, null, null);

        assertThat(policy.isStalePending(pending, NOW.plus(Duration.ofMinutes(14)))).isFalse();
        assertThat(policy.isStalePending(pending, NOW.plus(Duration.ofMinutes(15)))).isTrue();
        assertThatThrownBy(() -> policy.requireRetryAllowed(pending, 0))
                .isInstanceOf(EmailDeliveryStateConflictException.class);
    }

    @Test
    void commandsAndPortsDoNotAcceptActorRecipientOrAttachmentOverrides() {
        assertThat(RequestPaymentReceiptEmailCommand.class.getRecordComponents())
                .hasSize(1);
        assertThat(RequestAccessCredentialEmailCommand.class.getRecordComponents())
                .hasSize(1);
        assertThat(RetryEmailDeliveryCommand.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("deliveryId", "expectedVersion");
        assertThat(EmailSender.class.isInterface()).isTrue();
        assertThat(EmailDeliveryQuery.class.isInterface()).isTrue();
        assertThat(EmailDeliveryStore.class.isInterface()).isTrue();
    }

    private static EmailDeliveryDetails delivery(
            EmailDeliveryStatus status,
            int attemptCount,
            long version,
            EmailDeliveryFailureCode failureCode,
            String failureMessage) {
        Instant sentAt = status == EmailDeliveryStatus.SENT ? NOW.plusSeconds(2) : null;
        return new EmailDeliveryDetails(
                ID,
                EmailDeliveryType.PAYMENT_RECEIPT,
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                "ana.client@example.com",
                "Receipt",
                "v1",
                "PAYMENT_RECEIPT",
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "receipt.pdf",
                "application/pdf",
                20,
                "a".repeat(64),
                "b".repeat(64),
                status,
                attemptCount,
                failureCode,
                failureMessage,
                NOW,
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                sentAt,
                status == EmailDeliveryStatus.PENDING ? null : NOW.plusSeconds(1),
                NOW,
                NOW.plusSeconds(2),
                version);
    }
}
