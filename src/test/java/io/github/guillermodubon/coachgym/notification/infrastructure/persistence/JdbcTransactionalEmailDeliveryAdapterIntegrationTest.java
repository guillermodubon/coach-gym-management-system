package io.github.guillermodubon.coachgym.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import io.github.guillermodubon.coachgym.notification.EmailAttemptResult;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryAttemptDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryDuplicateException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryNotFoundException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryPage;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryQuery;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliverySearchQuery;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliverySortDirection;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliverySortField;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryStateConflictException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryStore;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryVersionConflictException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JdbcTransactionalEmailDeliveryAdapterIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    @Autowired private EmailDeliveryStore deliveryStore;
    @Autowired private EmailDeliveryQuery deliveryQuery;

    @BeforeEach
    void clearDeliveries() {
        jdbcTemplate.execute("truncate table gym.email_delivery_attempts, gym.email_deliveries");
    }

    @Test
    void persistsCanonicalPendingDeliveryAndReadsItByIdAndLogicalDigest() {
        UUID clientId = insertClient("persist");
        EmailDeliveryDetails pending = pending(clientId, UUID.randomUUID(), "a".repeat(64));

        EmailDeliveryDetails persisted = deliveryStore.createPending(pending);

        assertThat(deliveryQuery.findById(persisted.id())).contains(persisted);
        assertThat(deliveryQuery.findByIdempotencyKeyDigest(persisted.idempotencyKeyDigest()))
                .contains(persisted);
    }

    @Test
    void translatesLogicalDuplicatesAndOptimisticConflicts() {
        UUID clientId = insertClient("duplicate");
        EmailDeliveryDetails pending = pending(clientId, UUID.randomUUID(), "b".repeat(64));
        deliveryStore.createPending(pending);

        assertThatThrownBy(() -> deliveryStore.createPending(pending))
                .isInstanceOf(EmailDeliveryDuplicateException.class);

        appendSent(pending);
        assertThatThrownBy(() -> deliveryStore.finalizeAttempt(
                pending.id(), EmailDeliveryStatus.SENT, null, null,
                NOW.plusSeconds(1), NOW.plusSeconds(1), 0))
                .isInstanceOf(EmailDeliveryStateConflictException.class);
    }

    @Test
    void appendsImmutableAttemptAndFinalizesWithVersionedStatus() {
        UUID clientId = insertClient("finalize");
        EmailDeliveryDetails pending = pending(clientId, UUID.randomUUID(), "c".repeat(64));
        deliveryStore.createPending(pending);
        EmailDeliveryAttemptDetails attempt = sentAttempt(pending.id());

        assertThat(deliveryStore.appendAttempt(attempt)).isEqualTo(attempt);
        EmailDeliveryDetails sent = deliveryStore.finalizeAttempt(
                pending.id(), EmailDeliveryStatus.SENT, null, null,
                NOW.plusSeconds(10), NOW.plusSeconds(10), 0);

        assertThat(sent.status()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(sent.attemptCount()).isEqualTo(1);
        assertThat(sent.version()).isEqualTo(1);
        assertThatThrownBy(() -> deliveryStore.finalizeAttempt(
                pending.id(), EmailDeliveryStatus.SENT, null, null,
                NOW.plusSeconds(10), NOW.plusSeconds(10), 0))
                .isInstanceOf(EmailDeliveryStateConflictException.class);
    }

    @Test
    void appendAndFinalizeRollsBackTheAttemptWhenOptimisticLockingFails() {
        UUID clientId = insertClient("atomic-finalize");
        EmailDeliveryDetails pending = pending(clientId, UUID.randomUUID(), "f".repeat(64));
        deliveryStore.createPending(pending);
        EmailDeliveryAttemptDetails attempt = sentAttempt(pending.id());

        assertThatThrownBy(() -> deliveryStore.appendAttemptAndFinalize(
                attempt, EmailDeliveryStatus.SENT, null, null,
                attempt.completedAt(), attempt.completedAt(), 99))
                .isInstanceOf(EmailDeliveryVersionConflictException.class);

        assertThat(deliveryQuery.findById(pending.id())).contains(pending);
        assertThat(deliveryQuery.findAttempts(pending.id())).isEmpty();
    }

    @Test
    void returnsStableFilteredPagesWithAllowlistedSorting() {
        UUID clientId = insertClient("page");
        deliveryStore.createPending(pending(clientId, UUID.randomUUID(), "d".repeat(64)));
        deliveryStore.createPending(pending(clientId, UUID.randomUUID(), "e".repeat(64)));

        EmailDeliveryPage page = deliveryQuery.findAll(new EmailDeliverySearchQuery(
                EmailDeliveryType.PAYMENT_RECEIPT, EmailDeliveryStatus.PENDING,
                clientId, null, null, null, 0, 1,
                EmailDeliverySortField.REQUESTED_AT,
                EmailDeliverySortDirection.ASC));

        assertThat(page.items()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items().get(0).clientId()).isEqualTo(clientId);
    }

    @Test
    void reportsMissingDeliveryWithoutLeakingSqlDetails() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> deliveryStore.finalizeAttempt(
                missing, EmailDeliveryStatus.SENT, null, null,
                NOW, NOW, 0))
                .isInstanceOf(EmailDeliveryNotFoundException.class)
                .hasMessage("Email delivery could not be found.");
    }

    private EmailDeliveryAttemptDetails sentAttempt(UUID deliveryId) {
        return new EmailDeliveryAttemptDetails(
                UUID.randomUUID(), deliveryId, 1, EmailAttemptResult.SENT,
                NOW.plusSeconds(1), NOW.plusSeconds(2), null, null, adminId,
                "provider-message-1");
    }

    private void appendSent(EmailDeliveryDetails pending) {
        deliveryStore.appendAttempt(sentAttempt(pending.id()));
        deliveryStore.finalizeAttempt(
                pending.id(), EmailDeliveryStatus.SENT, null, null,
                NOW.plusSeconds(1), NOW.plusSeconds(1), 0);
    }

    private EmailDeliveryDetails pending(UUID clientId, UUID sourceId, String digest) {
        UUID actor = adminId;
        return new EmailDeliveryDetails(
                UUID.randomUUID(), EmailDeliveryType.PAYMENT_RECEIPT, sourceId, clientId,
                "recipient@example.com", "Your Coach Gym receipt", "receipt-v1",
                "PAYMENT_RECEIPT", sourceId, "payment-receipt-" + sourceId + ".pdf",
                "application/pdf", 128, digest, digest, EmailDeliveryStatus.PENDING, 0,
                null, null, NOW, actor, null, null, NOW, NOW, 0);
    }

    private UUID insertClient(String suffix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.clients
                    (id, first_name, last_name, email, phone, status, version)
                values (?, 'Email', ?, ?, '+50370000000', 'ACTIVE', 0)
                """, id, suffix, suffix + "@example.com");
        return id;
    }
}
