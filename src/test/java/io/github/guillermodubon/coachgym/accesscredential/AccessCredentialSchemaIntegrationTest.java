package io.github.guillermodubon.coachgym.accesscredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

class AccessCredentialSchemaIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    private static final OffsetDateTime ISSUED_AT = OffsetDateTime.of(
            2026, 9, 12, 10, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void clearCredentialFixtures() {
        // The production history trigger is append-only; TRUNCATE clears
        // isolated test tables without exercising a business delete path.
        jdbcTemplate.execute(
                "truncate table gym.access_records, "
                        + "gym.access_credential_history, gym.access_credentials");
    }

    @Test
    void currentFlywayChainContainsCredentialTablesConstraintsAndTriggers() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'gym'
                  and table_name in ('access_credentials', 'access_credential_history')
                order by table_name
                """, String.class);

        assertThat(tables)
                .containsExactly("access_credential_history", "access_credentials");

        List<String> constraints = jdbcTemplate.queryForList("""
                select constraint_name
                from information_schema.table_constraints
                where table_schema = 'gym'
                  and table_name in ('access_credentials', 'access_credential_history')
                """, String.class);

        assertThat(constraints)
                .contains(
                        "uq_access_credentials_code",
                        "uq_access_credentials_token_fingerprint",
                        "ck_access_credentials_status",
                        "ck_access_credentials_lifecycle_metadata",
                        "ck_access_credentials_content_type",
                        "ck_access_credentials_size_range",
                        "ck_access_credentials_checksum_sha256",
                        "ck_access_credentials_version_non_negative",
                        "ck_access_credential_history_transition",
                        "fk_access_credentials_client",
                        "fk_access_credentials_issued_by_user",
                        "fk_access_credentials_replaced_by",
                        "fk_access_credential_history_credential",
                        "fk_access_credential_history_changed_by_user");

        List<String> triggers = jdbcTemplate.queryForList("""
                select trigger_name
                from information_schema.triggers
                where trigger_schema = 'gym'
                  and event_object_table in ('access_credentials', 'access_credential_history')
                """, String.class);

        assertThat(triggers)
                .contains(
                        "trg_access_credentials_validate",
                        "trg_access_credentials_reject_delete",
                        "trg_access_credential_history_validate",
                        "trg_access_credential_history_append_only");

        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*)
                from flyway_schema_history
                where version = '23'
                  and success = true
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
    }

    @Test
    void enforcesOneActiveCredentialPerClientAndUniqueFingerprint() {
        ClientFixture client = createClient();
        insertCredential(client.id(), "CRED-UNIQUE-001", "a".repeat(64));

        assertThatThrownBy(() -> insertCredential(
                client.id(), "CRED-UNIQUE-002", "b".repeat(64)))
                .isInstanceOf(DataAccessException.class);

        ClientFixture secondClient = createClient();
        assertThatThrownBy(() -> insertCredential(
                secondClient.id(), "CRED-UNIQUE-003", "a".repeat(64)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsInvalidStatusTransitionsRevocationMetadataAndArtifacts() {
        ClientFixture client = createClient();

        assertThatThrownBy(() -> insertCredential(
                client.id(), "CRED-STATUS-001", "c".repeat(64),
                "REPLACED", null, null, null, null,
                "image/png", 32L, "d".repeat(64)))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertCredential(
                client.id(), "CRED-STATUS-002", "e".repeat(64),
                "REVOKED", null, null, "Lost credential", null,
                "image/png", 32L, "f".repeat(64)))
                .isInstanceOf(DataAccessException.class);

        UUID credentialId = insertCredential(
                client.id(), "CRED-STATUS-003", "1".repeat(64));

        assertThatCode(() -> jdbcTemplate.update("""
                update gym.access_credentials
                set status = 'REVOKED',
                    revoked_at = ?,
                    revoked_by_user_id = ?
                where id = ?
                """, ISSUED_AT.plusMinutes(5), adminId, credentialId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertCredential(
                client.id(), "CRED-STATUS-004", "2".repeat(64),
                "ACTIVE", null, null, null, null,
                "image/jpeg", 32L, "3".repeat(64)))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertCredential(
                client.id(), "CRED-STATUS-005", "4".repeat(64),
                "ACTIVE", null, null, null, null,
                "image/png", 0L, "5".repeat(64)))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertCredential(
                client.id(), "CRED-STATUS-006", "6".repeat(64),
                "ACTIVE", null, null, null, null,
                "image/png", 32L, "G".repeat(64)))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertCredential(
                client.id(), "CRED-STATUS-007", "7".repeat(64),
                "ACTIVE", null, null, null, null,
                "image/png", 1_048_577L, "8".repeat(64)))
                .isInstanceOf(DataAccessException.class);

        assertThatCode(() -> jdbcTemplate.update("""
                update gym.access_credentials
                set status = 'REVOKED',
                    revoked_at = ?,
                    revoked_by_user_id = ?,
                    revocation_reason = 'Lost credential'
                where id = ?
                """, ISSUED_AT.plusMinutes(5), adminId, credentialId))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                update gym.access_credentials
                set status = 'ACTIVE',
                    revoked_at = null,
                    revoked_by_user_id = null,
                    revocation_reason = null
                where id = ?
                """, credentialId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void replacementMustPointToAnActiveCredentialOfTheSameClient() {
        ClientFixture client = createClient();
        UUID originalId = insertCredential(
                client.id(), "CRED-REPLACE-001", "a".repeat(64));
        insertHistory(
                originalId,
                client.id(),
                null,
                "ACTIVE",
                null,
                ISSUED_AT,
                null);

        revoke(originalId, null, "Security review");
        UUID replacementId = insertCredential(
                client.id(), "CRED-REPLACE-002", "b".repeat(64));

        linkReplacement(originalId, replacementId);
        insertHistory(
                originalId,
                client.id(),
                "ACTIVE",
                "REVOKED",
                "Security review",
                ISSUED_AT.plusMinutes(5),
                replacementId);

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select status, replaced_by_credential_id
                from gym.access_credentials
                where id = ?
                """, originalId);
        assertThat(row)
                .containsEntry("status", "REVOKED")
                .containsEntry("replaced_by_credential_id", replacementId);

        ClientFixture otherClient = createClient();
        UUID originalOtherId = insertCredential(
                otherClient.id(), "CRED-REPLACE-003", "c".repeat(64));
        insertHistory(
                originalOtherId,
                otherClient.id(),
                null,
                "ACTIVE",
                null,
                ISSUED_AT,
                null);

        revoke(originalOtherId, null, "Wrong target");
        assertThatThrownBy(() -> linkReplacement(originalOtherId, replacementId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertHistory(
                originalOtherId,
                otherClient.id(),
                "ACTIVE",
                "REVOKED",
                "Lost credential",
                ISSUED_AT.plusMinutes(5),
                replacementId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void historyIsAppendOnlyAndCredentialDeletionIsNotAValidLifecycleOperation() {
        ClientFixture client = createClient();
        UUID credentialId = insertCredential(
                client.id(), "CRED-HISTORY-001", "e".repeat(64));
        insertHistory(
                credentialId,
                client.id(),
                null,
                "ACTIVE",
                null,
                ISSUED_AT,
                null);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                update gym.access_credential_history
                set reason = 'Tampered'
                where credential_id = ?
                """, credentialId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                delete from gym.access_credential_history
                where credential_id = ?
                """, credentialId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.access_credentials where id = ?", credentialId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.clients where id = ?", client.id()))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.users where id = ?", adminId))
                .isInstanceOf(DataAccessException.class);
    }

    private ClientFixture createClient() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.clients
                    (id, first_name, last_name, phone, status,
                     created_by_user_id, updated_by_user_id,
                     created_at, updated_at, version)
                values (?, 'Credential', 'Client', '+50370003001', 'ACTIVE',
                        ?, ?, ?, ?, 0)
                """, id, adminId, adminId, ISSUED_AT, ISSUED_AT);
        String code = jdbcTemplate.queryForObject(
                "select client_code from gym.clients where id = ?",
                String.class,
                id);
        return new ClientFixture(id, code);
    }

    private UUID insertCredential(UUID clientId, String code, String fingerprint) {
        return insertCredential(
                clientId, code, fingerprint, "ACTIVE", null, null, null, null,
                "image/png", 32L, "b".repeat(64));
    }

    private UUID insertCredential(
            UUID clientId,
            String code,
            String fingerprint,
            String status,
            OffsetDateTime revokedAt,
            UUID revokedBy,
            String reason,
            UUID replacementId,
            String contentType,
            long sizeBytes,
            String checksum) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.access_credentials
                    (id, client_id, credential_code, token_fingerprint,
                     token_scheme_version, payload_version, status, issued_at,
                     issued_by_user_id, revoked_at, revoked_by_user_id,
                     revocation_reason, replaced_by_credential_id, storage_key,
                     content_type, size_bytes, checksum_sha256, renderer_version)
                values (?, ?, ?, ?, 'sha256-v1', 'v1', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'qr-v1')
                """, id, clientId, code, fingerprint, status, ISSUED_AT, adminId,
                revokedAt, revokedBy, reason, replacementId,
                "access-credentials/" + id + ".png", contentType, sizeBytes, checksum);
        return id;
    }

    private void revoke(UUID credentialId, UUID replacementId, String reason) {
        jdbcTemplate.update("""
                update gym.access_credentials
                set status = 'REVOKED',
                    revoked_at = ?,
                    revoked_by_user_id = ?,
                    revocation_reason = ?,
                    replaced_by_credential_id = ?,
                    version = version + 1
                where id = ?
                """, ISSUED_AT.plusMinutes(5), adminId, reason, replacementId, credentialId);
    }

    private void linkReplacement(UUID credentialId, UUID replacementId) {
        jdbcTemplate.update("""
                update gym.access_credentials
                set replaced_by_credential_id = ?, version = version + 1
                where id = ?
                """, replacementId, credentialId);
    }

    private void insertHistory(
            UUID credentialId,
            UUID clientId,
            String previousStatus,
            String newStatus,
            String reason,
            OffsetDateTime occurredAt,
            UUID replacementId) {
        jdbcTemplate.update("""
                insert into gym.access_credential_history
                    (id, credential_id, client_id, previous_status, new_status,
                     reason, occurred_at, changed_by_user_id,
                     replacement_credential_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), credentialId, clientId, previousStatus,
                newStatus, reason, occurredAt, adminId, replacementId);
    }

    private record ClientFixture(UUID id, String code) {
    }
}
