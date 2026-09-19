package io.github.guillermodubon.coachgym.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class EmailDeliverySchemaIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    private static final OffsetDateTime REQUESTED_AT = OffsetDateTime.of(
            2026, 9, 15, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void clearEmailDeliveryFixtures() {
        jdbcTemplate.execute(
                "truncate table gym.email_delivery_claims, "
                        + "gym.email_delivery_attempts, gym.email_deliveries");
    }

    @Test
    void currentFlywayChainContainsDeliverySchemaAndIndexes() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'gym'
                  and table_name in ('email_deliveries', 'email_delivery_attempts')
                order by table_name
                """, String.class);

        assertThat(tables)
                .containsExactly("email_deliveries", "email_delivery_attempts");

        List<String> constraints = jdbcTemplate.queryForList("""
                select constraint_name
                from information_schema.table_constraints
                where constraint_schema = 'gym'
                  and table_name in ('email_deliveries', 'email_delivery_attempts')
                """, String.class);

        assertThat(constraints)
                .contains(
                        "uq_email_deliveries_idempotency_key_digest",
                        "ck_email_deliveries_type",
                        "ck_email_deliveries_recipient_snapshot",
                        "ck_email_deliveries_attachment_content_type",
                        "ck_email_deliveries_lifecycle_metadata",
                        "ck_email_deliveries_version_non_negative",
                        "uq_email_delivery_attempts_delivery_number",
                        "ck_email_delivery_attempts_result_metadata",
                        "fk_email_deliveries_client",
                        "fk_email_deliveries_requested_by_user",
                        "fk_email_delivery_attempts_delivery",
                        "fk_email_delivery_attempts_attempted_by_user");

        List<String> triggers = jdbcTemplate.queryForList("""
                select trigger_name
                from information_schema.triggers
                where trigger_schema = 'gym'
                  and event_object_table in (
                      'email_deliveries', 'email_delivery_attempts')
                """, String.class);

        assertThat(triggers)
                .contains(
                        "trg_email_deliveries_set_updated_at",
                        "trg_email_deliveries_validate_mutation",
                        "trg_email_delivery_attempts_validate",
                        "trg_email_delivery_attempts_append_only");

        List<String> indexes = jdbcTemplate.queryForList("""
                select indexname
                from pg_indexes
                where schemaname = 'gym'
                  and tablename in ('email_deliveries', 'email_delivery_attempts')
                """, String.class);

        assertThat(indexes)
                .contains(
                        "idx_email_deliveries_source",
                        "idx_email_deliveries_client",
                        "idx_email_deliveries_status",
                        "idx_email_deliveries_requested_at",
                        "idx_email_delivery_attempts_delivery_started_at");

        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*)
                from flyway_schema_history
                where version = '26'
                  and success = true
                """, Integer.class);
        assertThat(migrationCount).isEqualTo(1);
    }

    @Test
    void createsPendingDeliveryAndEnforcesLogicalIdempotency() {
        UUID clientId = insertClient();
        UUID deliveryId = insertPendingDelivery(clientId, "a".repeat(64));

        Map<String, Object> delivery = jdbcTemplate.queryForMap("""
                select status, attempt_count, version, recipient_snapshot,
                       attachment_content_type
                from gym.email_deliveries
                where id = ?
                """, deliveryId);

        assertThat(delivery)
                .containsEntry("status", "PENDING")
                .containsEntry("attempt_count", 0)
                .containsEntry("version", 0L)
                .containsEntry("recipient_snapshot", "ana@example.com")
                .containsEntry("attachment_content_type", "application/pdf");

        assertThatThrownBy(() -> insertPendingDelivery(clientId, "a".repeat(64)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsInvalidLifecycleAndAttachmentMetadata() {
        UUID clientId = insertClient();

        assertThatThrownBy(() -> insertDelivery(
                clientId,
                "b".repeat(64),
                "SENT",
                0,
                null,
                null,
                null,
                "application/pdf",
                128,
                "c".repeat(64)))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertPendingDelivery(
                clientId, "d".repeat(64), "image/png", 128, "e".repeat(64)))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertPendingDelivery(
                clientId, "f".repeat(64), "application/pdf", 0, "1".repeat(64)))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertPendingDelivery(
                clientId, "2".repeat(64), "application/pdf", 128, "G".repeat(64)))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertPendingDelivery(
                clientId, "3".repeat(64), "application/pdf", 128, "4".repeat(64),
                "Ana@Example.com"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void enforcesRestrictiveRelationshipsAndNoBusinessDeletion() {
        UUID clientId = insertClient();
        UUID deliveryId = insertPendingDelivery(clientId, "5".repeat(64));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.clients where id = ?", clientId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.users where id = ?", adminId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.email_deliveries where id = ?", deliveryId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void appendsSequentialAttemptsAndProtectsAttemptHistory() {
        UUID clientId = insertClient();
        UUID deliveryId = insertPendingDelivery(clientId, "6".repeat(64));
        UUID attemptId = insertAttempt(
                deliveryId,
                1,
                "FAILED",
                "TRANSPORT_REJECTED",
                "Provider rejected the message.");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                update gym.email_delivery_attempts
                set failure_message = 'tampered'
                where id = ?
                """, attemptId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.email_delivery_attempts where id = ?", attemptId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertAttempt(
                deliveryId,
                1,
                "FAILED",
                "TRANSPORT_REJECTED",
                "Duplicate number."))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertAttempt(
                deliveryId,
                3,
                "FAILED",
                "TRANSPORT_REJECTED",
                "Skipped number."))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void enforcesAttemptResultMetadataAndOptimisticVersion() {
        UUID clientId = insertClient();
        UUID deliveryId = insertPendingDelivery(clientId, "7".repeat(64));

        assertThatThrownBy(() -> insertAttempt(
                deliveryId,
                1,
                "SENT",
                "TRANSPORT_REJECTED",
                "Successful attempts cannot fail."))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertAttempt(
                deliveryId,
                1,
                "AMBIGUOUS",
                "TRANSPORT_REJECTED",
                "Ambiguous attempts require the dedicated code."))
                .isInstanceOf(DataAccessException.class);

        insertAttempt(
                deliveryId,
                1,
                "FAILED",
                "TRANSPORT_TIMEOUT",
                "The transport timed out.");
        markFailed(deliveryId, 1, 1);

        insertAttempt(
                deliveryId,
                2,
                "SENT",
                null,
                null);

        assertThatThrownBy(() -> updateDeliveryToSent(deliveryId, 1, 2))
                .isInstanceOf(DataAccessException.class);

        updateDeliveryToSent(deliveryId, 2, 2);

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select status, attempt_count, sent_at, version
                from gym.email_deliveries
                where id = ?
                """, deliveryId);
        assertThat(row)
                .containsEntry("status", "SENT")
                .containsEntry("attempt_count", 2)
                .containsEntry("version", 2L);
    }

    @Test
    void existingCoreRowsRemainCompatibleAndNoPayloadColumnsAreStored() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.gym_settings where id = 1",
                Integer.class)).isEqualTo(1);

        List<String> unsafeColumns = jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = 'gym'
                  and table_name in ('email_deliveries', 'email_delivery_attempts')
                  and lower(column_name) in (
                      'body', 'plain_text_body', 'html_body', 'message_body',
                      'payload', 'raw_payload', 'attachment_bytes', 'content')
                """, String.class);

        assertThat(unsafeColumns).isEmpty();
    }

    private UUID insertClient() {
        UUID id = UUID.randomUUID();
        String email = "email-schema-" + id + "@example.com";
        jdbcTemplate.update("""
                insert into gym.clients
                    (id, first_name, last_name, email, phone, status, version)
                values (?, 'Ana', 'Client', ?,
                        '+50370000000', 'ACTIVE', 0)
                """, id, email);
        return id;
    }

    private UUID insertPendingDelivery(UUID clientId, String digest) {
        return insertPendingDelivery(
                clientId, digest, "application/pdf", 128, "a".repeat(64),
                "ana@example.com");
    }

    private UUID insertPendingDelivery(
            UUID clientId,
            String digest,
            String contentType,
            long sizeBytes,
            String checksum) {
        return insertPendingDelivery(
                clientId, digest, contentType, sizeBytes, checksum,
                "ana@example.com");
    }

    private UUID insertPendingDelivery(
            UUID clientId,
            String digest,
            String contentType,
            long sizeBytes,
            String checksum,
            String recipient) {
        return insertDelivery(
                clientId,
                digest,
                "PENDING",
                0,
                null,
                null,
                null,
                contentType,
                sizeBytes,
                checksum,
                recipient);
    }

    private UUID insertDelivery(
            UUID clientId,
            String digest,
            String status,
            int attemptCount,
            String sentAt,
            String lastAttemptAt,
            String failureCode,
            String contentType,
            long sizeBytes,
            String checksum) {
        return insertDelivery(
                clientId,
                digest,
                status,
                attemptCount,
                sentAt,
                lastAttemptAt,
                failureCode,
                contentType,
                sizeBytes,
                checksum,
                "ana@example.com");
    }

    private UUID insertDelivery(
            UUID clientId,
            String digest,
            String status,
            int attemptCount,
            String sentAt,
            String lastAttemptAt,
            String failureCode,
            String contentType,
            long sizeBytes,
            String checksum,
            String recipient) {
        UUID id = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        String failureMessage = failureCode == null
                ? null : "Safe delivery failure.";

        jdbcTemplate.update("""
                insert into gym.email_deliveries
                    (id, delivery_type, source_resource_id, client_id,
                     recipient_snapshot, subject_snapshot, template_version,
                     attachment_resource_type, attachment_resource_id,
                     attachment_filename, attachment_content_type,
                     attachment_size_bytes, attachment_checksum_sha256,
                     idempotency_key_digest, status, attempt_count,
                     last_failure_code, last_failure_message, requested_at,
                     requested_by_user_id, sent_at, last_attempt_at,
                     created_at, updated_at, version)
                values (?, 'PAYMENT_RECEIPT', ?, ?, ?, 'Coach Gym receipt',
                        'v1', 'PAYMENT_RECEIPT', ?, 'receipt.pdf', ?,
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        cast(? as timestamptz), cast(? as timestamptz),
                        cast(? as timestamptz), 0)
                """,
                id,
                sourceId,
                clientId,
                recipient,
                attachmentId,
                contentType,
                sizeBytes,
                checksum,
                digest,
                status,
                attemptCount,
                failureCode,
                failureMessage,
                REQUESTED_AT,
                adminId,
                sentAt == null ? null : REQUESTED_AT.plusMinutes(5),
                lastAttemptAt == null ? null : REQUESTED_AT.plusMinutes(5),
                REQUESTED_AT,
                REQUESTED_AT);
        return id;
    }

    private UUID insertAttempt(
            UUID deliveryId,
            int attemptNumber,
            String result,
            String failureCode,
            String failureMessage) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.email_delivery_attempts
                    (id, delivery_id, attempt_number, result, started_at,
                     completed_at, failure_code, failure_message,
                     provider_message_id, attempted_by_user_id, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, null, ?, ?)
                """,
                id,
                deliveryId,
                attemptNumber,
                result,
                REQUESTED_AT.plusMinutes(attemptNumber),
                REQUESTED_AT.plusMinutes(attemptNumber).plusSeconds(1),
                failureCode,
                failureMessage,
                adminId,
                REQUESTED_AT.plusMinutes(attemptNumber));
        return id;
    }

    private void markFailed(UUID deliveryId, int attemptCount, long version) {
        jdbcTemplate.update("""
                update gym.email_deliveries
                set status = 'FAILED',
                    attempt_count = ?,
                    last_failure_code = 'TRANSPORT_TIMEOUT',
                    last_failure_message = 'The transport timed out.',
                    last_attempt_at = ?,
                    version = ?
                where id = ?
                """,
                attemptCount,
                REQUESTED_AT.plusMinutes(attemptCount),
                version,
                deliveryId);
    }

    private void updateDeliveryToSent(
            UUID deliveryId,
            long expectedVersion,
            int attemptCount) {
        jdbcTemplate.update("""
                update gym.email_deliveries
                set status = 'SENT',
                    attempt_count = ?,
                    last_failure_code = null,
                    last_failure_message = null,
                    sent_at = ?,
                    last_attempt_at = ?,
                    version = ?
                where id = ?
                """,
                attemptCount,
                REQUESTED_AT.plusMinutes(attemptCount).plusSeconds(2),
                REQUESTED_AT.plusMinutes(attemptCount),
                expectedVersion,
                deliveryId);
    }
}
