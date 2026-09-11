package io.github.guillermodubon.coachgym.payment.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptFailureCode;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentAttemptTransitionPolicyTest {

    private static final UUID ATTEMPT_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");

    @Test
    void permitsOnlyTheDefinedOpenAttemptTransitions() {
        assertThatCode(() -> PaymentAttemptTransitionPolicy.requireTransitionAllowed(
                ATTEMPT_ID, PaymentAttemptStatus.CREATED, PaymentAttemptStatus.PROCESSING))
                .doesNotThrowAnyException();
        assertThatCode(() -> PaymentAttemptTransitionPolicy.requireTransitionAllowed(
                ATTEMPT_ID, PaymentAttemptStatus.PROCESSING, PaymentAttemptStatus.SUCCEEDED))
                .doesNotThrowAnyException();
        assertThatCode(() -> PaymentAttemptTransitionPolicy.requireTransitionAllowed(
                ATTEMPT_ID, PaymentAttemptStatus.PROCESSING, PaymentAttemptStatus.EXPIRED))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidAndTerminalTransitions() {
        assertThatThrownBy(() -> PaymentAttemptTransitionPolicy.requireTransitionAllowed(
                ATTEMPT_ID, PaymentAttemptStatus.CREATED, PaymentAttemptStatus.SUCCEEDED))
                .isInstanceOf(PaymentAttemptStateConflictException.class);
        assertThatThrownBy(() -> PaymentAttemptTransitionPolicy.requireTransitionAllowed(
                ATTEMPT_ID, PaymentAttemptStatus.SUCCEEDED, PaymentAttemptStatus.FAILED))
                .isInstanceOf(PaymentAttemptStateConflictException.class);
    }

    @Test
    void enforcesTerminalOutcomeAndCompletionInvariants() {
        Instant completedAt = Instant.parse("2026-09-10T12:00:00Z");

        assertThatCode(() -> PaymentAttemptTransitionPolicy.requireSnapshotConsistency(
                PaymentAttemptStatus.SUCCEEDED, null, PAYMENT_ID, completedAt))
                .doesNotThrowAnyException();
        assertThatCode(() -> PaymentAttemptTransitionPolicy.requireSnapshotConsistency(
                PaymentAttemptStatus.FAILED,
                PaymentAttemptFailureCode.PROVIDER_DECLINED,
                null,
                completedAt))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> PaymentAttemptTransitionPolicy.requireSnapshotConsistency(
                PaymentAttemptStatus.PROCESSING, null, null, completedAt))
                .isInstanceOf(PaymentAttemptValidationException.class);
        assertThatThrownBy(() -> PaymentAttemptTransitionPolicy.requireSnapshotConsistency(
                PaymentAttemptStatus.SUCCEEDED, null, null, completedAt))
                .isInstanceOf(PaymentAttemptValidationException.class);
        assertThatThrownBy(() -> PaymentAttemptTransitionPolicy.requireSnapshotConsistency(
                PaymentAttemptStatus.EXPIRED, null, null, completedAt))
                .isInstanceOf(PaymentAttemptValidationException.class);
    }
}
