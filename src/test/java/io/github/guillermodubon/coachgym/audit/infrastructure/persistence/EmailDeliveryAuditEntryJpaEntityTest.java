package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryLifecycleEvent;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EmailDeliveryAuditEntryJpaEntityTest {

    private static final UUID DELIVERY_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000001");
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-15T12:00:00Z");

    @Test
    void mapsSentAttemptToSafeAuditMetadata() {
        EmailDeliveryLifecycleEvent event = new EmailDeliveryLifecycleEvent(
                DELIVERY_ID,
                EmailDeliveryType.PAYMENT_RECEIPT,
                SOURCE_ID,
                CLIENT_ID,
                "a***@example.com",
                EmailDeliveryStatus.SENT,
                1,
                null,
                ACTOR_ID,
                "coach-admin",
                OCCURRED_AT);

        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(event);

        assertThat(field(entry, "actionCode")).isEqualTo("EMAIL_DELIVERY_SENT");
        assertThat(field(entry, "resourceType")).isEqualTo("EMAIL_DELIVERY");
        assertThat(field(entry, "resourceId")).isEqualTo(DELIVERY_ID);
        assertThat(field(entry, "actorUserId")).isEqualTo(ACTOR_ID);
        assertThat(field(entry, "actorIdentifierSnapshot")).isEqualTo("coach-admin");
        assertThat(metadata(entry)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "deliveryType", "PAYMENT_RECEIPT",
                "status", "SENT",
                "attemptNumber", 1,
                "attemptResult", "SENT",
                "previousStatus", "PENDING",
                "sourceResourceId", SOURCE_ID.toString(),
                "clientId", CLIENT_ID.toString(),
                "maskedRecipient", "a***@example.com"));
        assertThat(metadata(entry).toString()).doesNotContain(
                "client@example.com", "smtp secret", "attachmentBytes",
                "password", "cardNumber", "rawToken");
    }

    @Test
    void mapsFailedRetryToRetryActionAndOnlyTheSafeFailureCode() {
        EmailDeliveryLifecycleEvent event = new EmailDeliveryLifecycleEvent(
                DELIVERY_ID,
                EmailDeliveryType.PAYMENT_RECEIPT,
                SOURCE_ID,
                CLIENT_ID,
                "a***@example.com",
                EmailDeliveryStatus.FAILED,
                2,
                EmailDeliveryFailureCode.TRANSPORT_TIMEOUT,
                ACTOR_ID,
                "coach-admin",
                OCCURRED_AT);

        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(event);

        assertThat(field(entry, "actionCode")).isEqualTo("EMAIL_DELIVERY_RETRIED");
        assertThat(metadata(entry)).containsEntry("failureCode", "TRANSPORT_TIMEOUT")
                .containsEntry("attemptNumber", 2)
                .doesNotContainKey("failureMessage");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(AuditEntryJpaEntity entry) {
        return (Map<String, Object>) ReflectionTestUtils.getField(entry, "metadata");
    }

    private static Object field(AuditEntryJpaEntity entry, String name) {
        return ReflectionTestUtils.getField(entry, name);
    }
}
