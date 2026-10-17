package io.github.guillermodubon.coachgym.branch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class BranchOwnershipExistingDataMigrationIntegrationTest {

    private static final UUID USER_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000002");
    private static final UUID PLAN_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000003");
    private static final UUID MEMBERSHIP_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000004");
    private static final UUID PERIOD_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000005");
    private static final UUID PAYMENT_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000006");
    private static final UUID ATTEMPT_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000007");
    private static final UUID RECEIPT_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000008");
    private static final UUID CREDENTIAL_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000009");
    private static final UUID ACCESS_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000010");
    private static final UUID CATEGORY_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000011");
    private static final UUID EQUIPMENT_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000012");
    private static final UUID INCIDENT_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000013");
    private static final UUID MAINTENANCE_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000014");
    private static final UUID RESOURCE_NOTIFICATION_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000015");
    private static final UUID GLOBAL_NOTIFICATION_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000016");
    private static final UUID RECEIPT_EMAIL_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000017");
    private static final UUID CREDENTIAL_EMAIL_ID = UUID.fromString(
            "71000000-0000-0000-0000-000000000018");
    private static final UUID INITIAL_BRANCH_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000002");
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Test
    void backfillsExistingOwnershipWithoutRewritingHistoryOrRegeneratingArtifacts() {
        String location = "filesystem:"
                + Path.of("src/main/resources/db/migration")
                        .toAbsolutePath()
                        .toString()
                        .replace('\\', '/');
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(location)
                .target("32")
                .load();
        flyway.migrate();

        JdbcTemplate jdbc = jdbcTemplate();
        seedLegacyRows(jdbc);
        Map<String, Integer> before = trackedCounts(jdbc);
        Map<String, Object> historyBefore = jdbc.queryForMap("""
                select payment.status as payment_status,
                       payment.amount as payment_amount,
                       access.decision as access_decision,
                       access.reason_code as access_reason,
                       equipment.status as equipment_status,
                       incident.status as incident_status,
                       maintenance.status as maintenance_status
                  from gym.payments payment
                  join gym.access_records access on access.id = ?
                  join gym.equipment equipment on equipment.id = ?
                  join gym.incidents incident on incident.id = ?
                  join gym.maintenances maintenance on maintenance.id = ?
                 where payment.id = ?
                """, ACCESS_ID, EQUIPMENT_ID, INCIDENT_ID, MAINTENANCE_ID, PAYMENT_ID);
        Map<String, Map<String, Object>> timestampsBefore = updatedTimestampSnapshot(jdbc);

        Flyway currentFlyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(location)
                .load();
        currentFlyway.migrate();
        currentFlyway.validate();

        assertBranch(jdbc, "clients", "home_branch_id", INITIAL_BRANCH_ID);
        assertBranch(jdbc, "memberships", "registered_at_branch_id", INITIAL_BRANCH_ID);
        assertBranch(jdbc, "membership_periods", "registered_at_branch_id", INITIAL_BRANCH_ID);
        assertBranch(jdbc, "payments", "registered_at_branch_id", INITIAL_BRANCH_ID);
        assertBranch(jdbc, "payment_attempts", "initiated_at_branch_id", INITIAL_BRANCH_ID);
        assertBranch(jdbc, "payment_receipts", "branch_id", INITIAL_BRANCH_ID);
        assertBranch(jdbc, "email_deliveries", "branch_id", INITIAL_BRANCH_ID);
        assertBranch(jdbc, "access_records", "branch_id", INITIAL_BRANCH_ID);
        assertBranch(jdbc, "equipment", "branch_id", INITIAL_BRANCH_ID);
        assertBranch(jdbc, "incidents", "branch_id", INITIAL_BRANCH_ID);
        assertBranch(jdbc, "maintenances", "branch_id", INITIAL_BRANCH_ID);
        assertBranch(jdbc, "notifications", "branch_id", INITIAL_BRANCH_ID,
                "where resource_type = 'EQUIPMENT'");

        assertThat(jdbc.queryForObject("""
                select branch_id from gym.email_deliveries where id = ?
                """, UUID.class, RECEIPT_EMAIL_ID)).isEqualTo(INITIAL_BRANCH_ID);
        assertThat(jdbc.queryForObject("""
                select branch_id from gym.email_deliveries where id = ?
                """, UUID.class, CREDENTIAL_EMAIL_ID)).isEqualTo(INITIAL_BRANCH_ID);
        assertThat(jdbc.queryForObject("""
                select branch_id from gym.notifications where id = ?
                """, UUID.class, GLOBAL_NOTIFICATION_ID)).isNull();
        assertThat(trackedCounts(jdbc)).isEqualTo(before);
        assertThat(jdbc.queryForMap("""
                select payment.status as payment_status,
                       payment.amount as payment_amount,
                       access.decision as access_decision,
                       access.reason_code as access_reason,
                       equipment.status as equipment_status,
                       incident.status as incident_status,
                       maintenance.status as maintenance_status
                  from gym.payments payment
                  join gym.access_records access on access.id = ?
                  join gym.equipment equipment on equipment.id = ?
                  join gym.incidents incident on incident.id = ?
                  join gym.maintenances maintenance on maintenance.id = ?
                 where payment.id = ?
                """, ACCESS_ID, EQUIPMENT_ID, INCIDENT_ID, MAINTENANCE_ID, PAYMENT_ID))
                .isEqualTo(historyBefore);
        assertThat(updatedTimestampSnapshot(jdbc)).isEqualTo(timestampsBefore);
        assertThat(jdbc.queryForObject("""
                select count(*) from gym.payment_status_history
                 where payment_id = ?
                """, Integer.class, PAYMENT_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from gym.email_delivery_attempts where delivery_id in (?, ?)
                """, Integer.class, RECEIPT_EMAIL_ID, CREDENTIAL_EMAIL_ID)).isZero();
        assertThat(jdbc.queryForObject("""
                select delete_rule
                  from information_schema.referential_constraints
                 where constraint_schema = 'gym'
                   and constraint_name = 'fk_email_deliveries_branch'
                """, String.class)).isEqualTo("RESTRICT");
    }

    private static void seedLegacyRows(JdbcTemplate jdbc) {
        jdbc.update("""
                insert into gym.users
                    (id, username, email, password_hash, first_name, last_name, status)
                values (?, 'branch-migration-user', 'branch-migration@example.com',
                        'migration-fixture-hash', 'Migration', 'Fixture', 'ACTIVE')
                """, USER_ID);
        jdbc.update("""
                insert into gym.clients (id, first_name, last_name, phone, created_by_user_id)
                values (?, 'Legacy', 'Client', '5550100', ?)
                """, CLIENT_ID, USER_ID);
        jdbc.update("""
                insert into gym.membership_plans
                    (id, name, duration_value, duration_unit, list_price, currency)
                values (?, 'Migration Plan', 1, 'MONTH', 25.00, 'USD')
                """, PLAN_ID);
        jdbc.update("""
                insert into gym.memberships (id, client_id, status, created_by_user_id)
                values (?, ?, 'ACTIVE', ?)
                """, MEMBERSHIP_ID, CLIENT_ID, USER_ID);
        jdbc.update("""
                insert into gym.membership_periods
                    (id, membership_id, period_number, period_source, membership_plan_id,
                     plan_code_snapshot, plan_name_snapshot, duration_value_snapshot,
                     duration_unit_snapshot, list_price, currency, discount_amount,
                     final_price, starts_on, base_ends_on, effective_ends_on, created_by_user_id)
                values (?, ?, 1, 'INITIAL', ?, 'PLAN-MIGRATION', 'Migration Plan',
                        1, 'MONTH', 25.00, 'USD', 0, 25.00,
                        date '2026-01-01', date '2026-02-01', date '2026-02-01', ?)
                """, PERIOD_ID, MEMBERSHIP_ID, PLAN_ID, USER_ID);
        jdbc.update("""
                insert into gym.payments
                    (id, client_id, membership_id, membership_period_id, amount, currency,
                     payment_method, status, paid_at, registered_by_user_id)
                values (?, ?, ?, ?, 25.00, 'USD', 'CASH', 'PAID', current_timestamp, ?)
                """, PAYMENT_ID, CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID, USER_ID);
        jdbc.update("""
                insert into gym.payment_status_history
                    (id, payment_id, new_status, reason, occurred_at, changed_by_user_id)
                values (?, ?, 'PAID', 'Legacy confirmed payment', current_timestamp, ?)
                """, UUID.fromString("71000000-0000-0000-0000-000000000019"),
                PAYMENT_ID, USER_ID);
        jdbc.update("""
                insert into gym.payment_attempts
                    (id, client_id, membership_id, membership_period_id, provider, status,
                     expected_amount, currency, created_by_user_id)
                values (?, ?, ?, ?, 'STRIPE', 'CREATED', 25.00, 'USD', ?)
                """, ATTEMPT_ID, CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID, USER_ID);
        jdbc.update("""
                insert into gym.payment_receipts
                    (id, receipt_number, payment_id, payment_code_snapshot,
                     payment_status_snapshot, client_code_snapshot,
                     client_display_name_snapshot, membership_code_snapshot,
                     plan_name_snapshot, membership_period_number, period_starts_on,
                     period_ends_on, list_price, discount_amount, amount, currency,
                     payment_method, paid_at, generated_at, generated_by_user_id,
                     storage_key, content_type, size_bytes, checksum_sha256)
                select ?, 'RCPT-MIGRATION-001', payment.id, payment.payment_code, 'PAID',
                       client.client_code, 'Legacy Client', membership.membership_code,
                       'Migration Plan', 1, date '2026-01-01', date '2026-02-01',
                       25.00, 0, 25.00, 'USD', 'CASH', payment.paid_at,
                       payment.paid_at, ?, 'receipts/migration.pdf',
                       'application/pdf', 64, repeat('a', 64)
                  from gym.payments payment
                  join gym.clients client on client.id = payment.client_id
                  join gym.memberships membership on membership.id = payment.membership_id
                 where payment.id = ?
                """, RECEIPT_ID, USER_ID, PAYMENT_ID);
        jdbc.update("""
                insert into gym.access_credentials
                    (id, client_id, credential_code, token_fingerprint,
                     token_scheme_version, payload_version, status, issued_at,
                     issued_by_user_id, storage_key, content_type, size_bytes,
                     checksum_sha256, renderer_version)
                values (?, ?, 'MIGRATION-CREDENTIAL', repeat('b', 64),
                        'sha256-v1', 'v1', 'ACTIVE', current_timestamp, ?,
                        'credentials/migration.png', 'image/png', 64, repeat('c', 64), 'qr-v1')
                """, CREDENTIAL_ID, CLIENT_ID, USER_ID);
        jdbc.update("""
                insert into gym.access_records
                    (id, entered_code, client_id, client_code_snapshot, decision,
                     reason_code, details, occurred_at, recorded_by_user_id,
                     identification_source, access_credential_id)
                select ?, 'MIGRATION-CREDENTIAL', client.id, client.client_code,
                       'ALLOWED', 'ACCESS_ALLOWED', 'Legacy access history',
                       current_timestamp, ?, 'QR_CREDENTIAL', ?
                  from gym.clients client where client.id = ?
                """, ACCESS_ID, USER_ID, CREDENTIAL_ID, CLIENT_ID);
        jdbc.update("""
                insert into gym.equipment_categories (id, name, is_active)
                values (?, 'Migration Equipment', true)
                """, CATEGORY_ID);
        jdbc.update("""
                insert into gym.equipment (id, equipment_category_id, name, created_by_user_id)
                values (?, ?, 'Legacy Treadmill', ?)
                """, EQUIPMENT_ID, CATEGORY_ID, USER_ID);
        jdbc.update("""
                insert into gym.incidents
                    (id, equipment_id, priority, description, reported_by_user_id)
                values (?, ?, 'LOW', 'Legacy incident', ?)
                """, INCIDENT_ID, EQUIPMENT_ID, USER_ID);
        jdbc.update("""
                insert into gym.maintenances
                    (id, equipment_id, incident_id, maintenance_type, scheduled_on,
                     currency, created_by_user_id)
                values (?, ?, ?, 'PREVENTIVE', date '2026-02-01', 'USD', ?)
                """, MAINTENANCE_ID, EQUIPMENT_ID, INCIDENT_ID, USER_ID);
        jdbc.update("""
                insert into gym.notifications
                    (id, recipient_user_id, notification_type, title, body,
                     resource_type, resource_id)
                values (?, ?, 'SYSTEM', 'Legacy operation', 'Existing notice', 'EQUIPMENT', ?),
                       (?, ?, 'SYSTEM', 'Global notice', 'Existing global notice', null, null)
                """, RESOURCE_NOTIFICATION_ID, USER_ID, EQUIPMENT_ID,
                GLOBAL_NOTIFICATION_ID, USER_ID);
        insertEmailDelivery(jdbc, RECEIPT_EMAIL_ID, "PAYMENT_RECEIPT", RECEIPT_ID,
                "receipt-migration-digest");
        insertEmailDelivery(jdbc, CREDENTIAL_EMAIL_ID, "ACCESS_CREDENTIAL", CREDENTIAL_ID,
                "credential-migration-digest");
    }

    private static void insertEmailDelivery(
            JdbcTemplate jdbc, UUID id, String type, UUID sourceId, String digest) {
        String digestHex = String.format("%064x", digest.hashCode() & 0xffffffffL);
        jdbc.update("""
                insert into gym.email_deliveries
                    (id, delivery_type, source_resource_id, client_id,
                     recipient_snapshot, subject_snapshot, template_version,
                     attachment_resource_type, attachment_resource_id,
                     attachment_filename, attachment_content_type,
                     attachment_size_bytes, attachment_checksum_sha256,
                     idempotency_key_digest, requested_at, requested_by_user_id)
                values (?, ?, ?, ?, 'legacy@example.com', 'Migration fixture', 'v1',
                        ?, ?, ?, ?, 64, repeat('d', 64), ?, current_timestamp, ?)
                """, id, type, sourceId, CLIENT_ID, type, sourceId,
                type.equals("PAYMENT_RECEIPT") ? "payment-receipt.pdf" : "access-credential.png",
                type.equals("PAYMENT_RECEIPT") ? "application/pdf" : "image/png",
                digestHex, USER_ID);
    }

    private static Map<String, Integer> trackedCounts(JdbcTemplate jdbc) {
        return Map.ofEntries(
                Map.entry("clients", count(jdbc, "clients")),
                Map.entry("memberships", count(jdbc, "memberships")),
                Map.entry("membership_periods", count(jdbc, "membership_periods")),
                Map.entry("payments", count(jdbc, "payments")),
                Map.entry("payment_attempts", count(jdbc, "payment_attempts")),
                Map.entry("payment_receipts", count(jdbc, "payment_receipts")),
                Map.entry("email_deliveries", count(jdbc, "email_deliveries")),
                Map.entry("access_records", count(jdbc, "access_records")),
                Map.entry("equipment", count(jdbc, "equipment")),
                Map.entry("incidents", count(jdbc, "incidents")),
                Map.entry("maintenances", count(jdbc, "maintenances")),
                Map.entry("notifications", count(jdbc, "notifications")),
                Map.entry("audit_entries", count(jdbc, "audit_entries")),
                Map.entry("payment_status_history", count(jdbc, "payment_status_history")),
                Map.entry("email_delivery_attempts", count(jdbc, "email_delivery_attempts")));
    }

    private static Map<String, Map<String, Object>> updatedTimestampSnapshot(JdbcTemplate jdbc) {
        return Map.ofEntries(
                Map.entry("client", jdbc.queryForMap(
                        "select created_at, updated_at, version from gym.clients where id = ?", CLIENT_ID)),
                Map.entry("membership", jdbc.queryForMap(
                        "select created_at, updated_at, version from gym.memberships where id = ?", MEMBERSHIP_ID)),
                Map.entry("period", jdbc.queryForMap(
                        "select created_at, updated_at, version from gym.membership_periods where id = ?", PERIOD_ID)),
                Map.entry("payment", jdbc.queryForMap(
                        "select created_at, updated_at, version from gym.payments where id = ?", PAYMENT_ID)),
                Map.entry("attempt", jdbc.queryForMap(
                        "select created_at, updated_at, version from gym.payment_attempts where id = ?", ATTEMPT_ID)),
                Map.entry("receipt", jdbc.queryForMap(
                        "select created_at, version from gym.payment_receipts where id = ?", RECEIPT_ID)),
                Map.entry("receiptEmail", jdbc.queryForMap(
                        "select created_at, updated_at, version from gym.email_deliveries where id = ?",
                        RECEIPT_EMAIL_ID)),
                Map.entry("credentialEmail", jdbc.queryForMap(
                        "select created_at, updated_at, version from gym.email_deliveries where id = ?",
                        CREDENTIAL_EMAIL_ID)),
                Map.entry("access", jdbc.queryForMap(
                        "select occurred_at, decision, reason_code from gym.access_records where id = ?",
                        ACCESS_ID)),
                Map.entry("equipment", jdbc.queryForMap(
                        "select created_at, updated_at, version from gym.equipment where id = ?", EQUIPMENT_ID)),
                Map.entry("incident", jdbc.queryForMap(
                        "select created_at, updated_at, version from gym.incidents where id = ?", INCIDENT_ID)),
                Map.entry("maintenance", jdbc.queryForMap(
                        "select created_at, updated_at, version from gym.maintenances where id = ?",
                        MAINTENANCE_ID)),
                Map.entry("resourceNotification", jdbc.queryForMap(
                        "select created_at, updated_at, version from gym.notifications where id = ?",
                        RESOURCE_NOTIFICATION_ID)),
                Map.entry("globalNotification", jdbc.queryForMap(
                        "select created_at, updated_at, version from gym.notifications where id = ?",
                        GLOBAL_NOTIFICATION_ID)));
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("select count(*) from gym." + table, Integer.class);
    }

    private static void assertBranch(
            JdbcTemplate jdbc, String table, String column, UUID expected, String... predicate) {
        String filter = predicate.length == 0 ? "" : " " + predicate[0];
        List<UUID> values = jdbc.query(
                "select " + column + " from gym." + table + filter,
                (rs, row) -> rs.getObject(column, UUID.class));
        assertThat(values).isNotEmpty().containsOnly(expected);
        Integer nullCount = jdbc.queryForObject(
                "select count(*) from gym." + table + " where " + column + " is null"
                        + (predicate.length == 0 ? "" : " and " + predicate[0].replaceFirst("^where ", "")),
                Integer.class);
        assertThat(nullCount).isZero();
    }

    private static JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }
}
