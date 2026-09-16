package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryLifecycleEvent;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

class EmailDeliveryAuditIntegrationTest extends AbstractIncidentApiIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    void persistsSafeJsonbAuditForSentDelivery() {
        UUID deliveryId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-15T12:00:00Z");

        eventPublisher.publishEvent(new EmailDeliveryLifecycleEvent(
                deliveryId,
                EmailDeliveryType.PAYMENT_RECEIPT,
                sourceId,
                clientId,
                "a***@example.com",
                EmailDeliveryStatus.SENT,
                1,
                null,
                adminId,
                ADMIN_USERNAME,
                occurredAt));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select action_code, resource_type, resource_id,
                       resource_code_snapshot, actor_user_id,
                       actor_identifier_snapshot, occurred_at,
                       metadata ->> 'deliveryType' as delivery_type,
                       metadata ->> 'status' as status,
                       metadata ->> 'attemptNumber' as attempt_number,
                       metadata ->> 'sourceResourceId' as source_resource_id,
                       metadata ->> 'clientId' as client_id,
                       metadata ->> 'maskedRecipient' as masked_recipient,
                       metadata::text as metadata
                from gym.audit_entries
                where resource_type = 'EMAIL_DELIVERY'
                  and resource_id = ?
                """, deliveryId);

        assertThat(row)
                .containsEntry("action_code", "EMAIL_DELIVERY_SENT")
                .containsEntry("resource_type", "EMAIL_DELIVERY")
                .containsEntry("resource_id", deliveryId)
                .containsEntry("resource_code_snapshot", "PAYMENT_RECEIPT")
                .containsEntry("actor_user_id", adminId)
                .containsEntry("actor_identifier_snapshot", ADMIN_USERNAME)
                .containsEntry("delivery_type", "PAYMENT_RECEIPT")
                .containsEntry("status", "SENT")
                .containsEntry("attempt_number", "1")
                .containsEntry("source_resource_id", sourceId.toString())
                .containsEntry("client_id", clientId.toString())
                .containsEntry("masked_recipient", "a***@example.com");
        assertThat(row.get("occurred_at"))
                .isInstanceOfAny(OffsetDateTime.class, java.sql.Timestamp.class);
        assertThat(row.get("metadata").toString())
                .doesNotContain("a.client@example.com", "messageBody", "attachmentBytes",
                        "smtp secret", "rawResponse", "password", "cardNumber");
    }

    @Test
    void recordsRetryAsOneBoundedFailureActionWithoutTheFailureMessage() {
        UUID deliveryId = UUID.randomUUID();

        eventPublisher.publishEvent(new EmailDeliveryLifecycleEvent(
                deliveryId,
                EmailDeliveryType.ACCESS_CREDENTIAL,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "a***@example.com",
                EmailDeliveryStatus.FAILED,
                2,
                EmailDeliveryFailureCode.TRANSPORT_TIMEOUT,
                adminId,
                ADMIN_USERNAME,
                Instant.parse("2026-09-15T12:00:01Z")));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select action_code,
                       metadata ->> 'failureCode' as failure_code,
                       metadata ->> 'attemptNumber' as attempt_number,
                       metadata::text as metadata
                from gym.audit_entries
                where resource_type = 'EMAIL_DELIVERY'
                  and resource_id = ?
                """, deliveryId);

        assertThat(row)
                .containsEntry("action_code", "EMAIL_DELIVERY_RETRIED")
                .containsEntry("failure_code", "TRANSPORT_TIMEOUT")
                .containsEntry("attempt_number", "2");
        assertThat(row.get("metadata").toString())
                .doesNotContain("failureMessage", "SMTP delivery timed out");
    }
}
