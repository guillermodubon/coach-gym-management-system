package io.github.guillermodubon.coachgym.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class QrAccessSchemaIntegrationTest extends AbstractAccessApiIntegrationTest {

    @BeforeEach
    void clearQrCredentialFixtures() {
        jdbcTemplate.execute(
                "truncate table gym.access_records, "
                        + "gym.access_credential_history, gym.access_credentials");
    }

    @Test
    void schemaExposesSafeQrColumnsConstraintsTriggersAndDuplicateIndex() {
        List<String> columns = jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = 'gym'
                  and table_name = 'access_records'
                  and column_name in ('identification_source', 'access_credential_id')
                order by column_name
                """, String.class);

        assertThat(columns)
                .containsExactly("access_credential_id", "identification_source");

        Map<String, Object> sourceColumn = jdbcTemplate.queryForMap("""
                select is_nullable, column_default
                from information_schema.columns
                where table_schema = 'gym'
                  and table_name = 'access_records'
                  and column_name = 'identification_source'
                """);
        assertThat(sourceColumn.get("is_nullable")).isEqualTo("NO");
        assertThat(sourceColumn.get("column_default").toString())
                .contains("'UNKNOWN'::character varying");

        List<String> constraints = jdbcTemplate.queryForList("""
                select constraint_name
                from information_schema.table_constraints
                where table_schema = 'gym'
                  and table_name = 'access_records'
                """, String.class);
        assertThat(constraints)
                .contains(
                        "ck_access_records_identification_source",
                        "ck_access_records_qr_credential_metadata",
                        "fk_access_records_access_credential");

        String deleteRule = jdbcTemplate.queryForObject("""
                select delete_rule
                from information_schema.referential_constraints
                where constraint_schema = 'gym'
                  and constraint_name = 'fk_access_records_access_credential'
                """, String.class);
        assertThat(deleteRule).isEqualTo("RESTRICT");

        List<String> triggers = jdbcTemplate.queryForList("""
                select trigger_name
                from information_schema.triggers
                where trigger_schema = 'gym'
                  and event_object_table = 'access_records'
                """, String.class);
        assertThat(triggers)
                .contains(
                        "trg_access_records_validate_qr_metadata",
                        "trg_access_records_append_only");

        String indexDefinition = jdbcTemplate.queryForObject("""
                select indexdef
                from pg_indexes
                where schemaname = 'gym'
                  and tablename = 'access_records'
                  and indexname = 'idx_access_records_qr_credential_result_occurred_at'
                """, String.class);
        assertThat(indexDefinition.toLowerCase(Locale.ROOT))
                .contains("access_credential_id", "decision", "occurred_at", "id")
                .contains("identification_source", "qr_credential");

        Integer duplicateDefinitionCount = jdbcTemplate.queryForObject("""
                select count(*)
                from pg_indexes
                where schemaname = 'gym'
                  and indexdef = ?
                """, Integer.class, indexDefinition);
        assertThat(duplicateDefinitionCount).isEqualTo(1);
    }

    @Test
    void existingManualRowsKeepAnExplicitUnknownSourceByDefault() {
        UUID id = insertAccessRow(
                "CLI-MANUAL-001",
                null,
                null,
                null,
                null,
                null,
                "DENIED",
                "IDENTIFIER_NOT_FOUND",
                java.time.Instant.now(),
                userId(ADMIN_USERNAME));

        assertThat(accessRow(id))
                .containsEntry("identification_source", "UNKNOWN")
                .containsEntry("access_credential_id", null);
    }

    @Test
    void validQrAttemptPersistsOnlyCredentialReferenceAndSafeSource() {
        ClientFixture client = createClient("ACTIVE");
        UUID credentialId = insertCredential(client);

        UUID accessRecordId = insertQrRecord(
                client,
                credentialId,
                "ALLOWED",
                "ACCESS_ALLOWED");

        assertThat(accessRow(accessRecordId))
                .containsEntry("identification_source", "QR_CREDENTIAL")
                .containsEntry("access_credential_id", credentialId)
                .containsEntry("entered_code", "QR_CREDENTIAL");
    }

    @Test
    void duplicateReasonCodeIsPersistableForQrAttempts() {
        ClientFixture client = createClient("ACTIVE");
        UUID credentialId = insertCredential(client);

        UUID accessRecordId = insertQrRecord(
                client,
                credentialId,
                "DENIED",
                "DUPLICATE_CHECK_IN");

        assertThat(accessRow(accessRecordId).get("reason_code"))
                .isEqualTo("DUPLICATE_CHECK_IN");
    }

    @Test
    void rejectsInvalidQrSourceAndCredentialCombinations() {
        ClientFixture client = createClient("ACTIVE");
        UUID credentialId = insertCredential(client);

        assertThatThrownBy(() -> insertQrRecord(client, null, "ALLOWED", "ACCESS_ALLOWED"))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertRecordWithSource(
                client,
                credentialId,
                "CLIENT_CODE",
                "ALLOWED",
                "ACCESS_ALLOWED"))
                .isInstanceOf(DataAccessException.class);

        ClientFixture otherClient = createClient("ACTIVE");
        assertThatThrownBy(() -> insertQrRecord(
                otherClient,
                credentialId,
                "ALLOWED",
                "ACCESS_ALLOWED"))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertRecordWithSource(
                client,
                UUID.randomUUID(),
                "QR_CREDENTIAL",
                "ALLOWED",
                "ACCESS_ALLOWED"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void referencedCredentialsAndAccessAttemptsCannotBeDeletedOrMutated() {
        ClientFixture client = createClient("ACTIVE");
        UUID credentialId = insertCredential(client);
        UUID accessRecordId = insertQrRecord(
                client,
                credentialId,
                "ALLOWED",
                "ACCESS_ALLOWED");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.access_credentials where id = ?", credentialId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update gym.access_records set details = 'tampered' where id = ?",
                accessRecordId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.access_records where id = ?", accessRecordId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void noRawPayloadOrTokenColumnIsAddedToAccessRecords() {
        List<String> unsafeColumns = jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = 'gym'
                  and table_name = 'access_records'
                  and lower(column_name) in (
                      'token', 'raw_token', 'payload', 'raw_payload',
                      'qr_payload', 'token_value')
                """, String.class);

        assertThat(unsafeColumns).isEmpty();
    }

    @Test
    void cleanMigrationChainIncludesV25() {
        Integer installed = jdbcTemplate.queryForObject("""
                select count(*)
                from flyway_schema_history
                where version = '25'
                  and success = true
                """, Integer.class);
        assertThat(installed).isEqualTo(1);

        String latestVersion = jdbcTemplate.queryForObject("""
                select version
                from flyway_schema_history
                where success = true
                order by installed_rank desc
                limit 1
                """, String.class);
        assertThat(latestVersion).isEqualTo("25");
    }

    private UUID insertCredential(ClientFixture client) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.access_credentials
                    (id, client_id, credential_code, token_fingerprint,
                     token_scheme_version, payload_version, status, issued_at,
                     issued_by_user_id, storage_key, content_type, size_bytes,
                     checksum_sha256, renderer_version)
                values (?, ?, ?, ?, 'sha256-v1', 'v1', 'ACTIVE', current_timestamp,
                        ?, ?, 'image/png', 32, ?, 'qr-v1')
                """, id, client.id(), "QR-" + id, "a".repeat(64),
                userId(ADMIN_USERNAME), "access-credentials/" + id + ".png",
                "b".repeat(64));
        return id;
    }

    private UUID insertQrRecord(
            ClientFixture client,
            UUID credentialId,
            String decision,
            String reasonCode) {
        return insertRecordWithSource(
                client,
                credentialId,
                "QR_CREDENTIAL",
                decision,
                reasonCode);
    }

    private UUID insertRecordWithSource(
            ClientFixture client,
            UUID credentialId,
            String source,
            String decision,
            String reasonCode) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.access_records
                    (id, entered_code, client_id, client_code_snapshot,
                     decision, reason_code, details, occurred_at,
                     recorded_by_user_id, identification_source, access_credential_id)
                values (?, ?, ?, ?, ?, ?, 'Schema integration row', current_timestamp,
                        ?, ?, ?)
                """, id, "QR_CREDENTIAL", client.id(), client.code(), decision,
                reasonCode, userId(ADMIN_USERNAME), source, credentialId);
        return id;
    }
}
