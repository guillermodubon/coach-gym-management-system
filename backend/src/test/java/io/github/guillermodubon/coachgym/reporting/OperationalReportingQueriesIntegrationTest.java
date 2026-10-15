package io.github.guillermodubon.coachgym.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.equipment.EquipmentReportingQuery;
import io.github.guillermodubon.coachgym.equipment.EquipmentReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportingQuery;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceReportingQuery;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryBranchReportingSummary;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryReportingQuery;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryReportingSummary;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class OperationalReportingQueriesIntegrationTest extends AbstractDashboardApiIntegrationTest {

    private static final UUID INITIAL_BRANCH_ID =
            UUID.fromString("7b0bf7d5-5184-43d2-8f9a-200000000002");
    private static final LocalDate RANGE_FROM = LocalDate.of(2099, 9, 1);
    private static final LocalDate RANGE_UNTIL = LocalDate.of(2099, 10, 1);
    private static final ZoneId REPORT_ZONE = ZoneId.of("America/El_Salvador");

    @Autowired private EquipmentReportingQuery equipmentReportingQuery;
    @Autowired private IncidentReportingQuery incidentReportingQuery;
    @Autowired private MaintenanceReportingQuery maintenanceReportingQuery;
    @Autowired private EmailDeliveryReportingQuery emailDeliveryReportingQuery;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void operationMetricsUseOneCanonicalRowAndPreserveInactiveBranchHistory() {
        UUID inactiveBranch = insertBranch("Inactive historical reporting fixture");
        UUID outOfService = insertEquipment(inactiveBranch, "OUT_OF_SERVICE", "Out of service");
        UUID retired = insertRetiredEquipment(inactiveBranch);

        Instant from = RANGE_FROM.atStartOfDay(REPORT_ZONE).toInstant();
        Instant until = RANGE_UNTIL.atStartOfDay(REPORT_ZONE).toInstant();
        UUID openIncident = insertIncident(
                equipmentId, INITIAL_BRANCH_ID, "OPEN", "LOW", from, null);
        UUID resolvedIncident = insertIncident(
                outOfService, inactiveBranch, "RESOLVED", "CRITICAL",
                from.plusSeconds(3600), from.plusSeconds(7200));
        insertIncident(retired, inactiveBranch, "IN_PROGRESS", "MEDIUM", until, null);
        insertIncidentHistory(resolvedIncident, "OPEN", "IN_PROGRESS", from.plusSeconds(4000));
        insertIncidentHistory(resolvedIncident, "IN_PROGRESS", "RESOLVED", from.plusSeconds(7200));

        LocalDate operationalDate = LocalDate.of(2099, 9, 15);
        insertMaintenance(equipmentId, INITIAL_BRANCH_ID, "SCHEDULED", operationalDate.minusDays(1), false);
        insertMaintenance(equipmentId, INITIAL_BRANCH_ID, "SCHEDULED", operationalDate, false);
        insertMaintenance(outOfService, inactiveBranch, "IN_PROGRESS", operationalDate.minusDays(4), false);
        insertMaintenance(retired, inactiveBranch, "COMPLETED", operationalDate.minusDays(5), true);
        jdbcTemplate.update("update gym.gym_branches set status = 'INACTIVE' where id = ?", inactiveBranch);

        ReportingQueryWindow window = new ReportingQueryWindow(RANGE_FROM, RANGE_UNTIL, REPORT_ZONE);
        ReportingQueryScope organization = ReportingQueryScope.organization();
        ReportingQueryScope bothBranches = ReportingQueryScope.branches(
                List.of(INITIAL_BRANCH_ID, inactiveBranch));

        EquipmentReportingSummary equipment = equipmentReportingQuery.summarize(organization);
        assertThat(equipment.totalEquipment()).isEqualTo(3);
        assertThat(equipment.outOfServiceEquipment()).isEqualTo(1);
        assertThat(equipment.countsByStatus())
                .containsEntry(io.github.guillermodubon.coachgym.equipment.EquipmentStatus.AVAILABLE, 1L)
                .containsEntry(io.github.guillermodubon.coachgym.equipment.EquipmentStatus.OUT_OF_SERVICE, 1L)
                .containsEntry(io.github.guillermodubon.coachgym.equipment.EquipmentStatus.RETIRED, 1L);
        var equipmentByBranch = equipmentReportingQuery.summarizeByBranch(bothBranches);
        assertThat(equipmentByBranch).hasSize(2);
        assertThat(equipmentByBranch.stream().mapToLong(value -> value.metrics().totalEquipment()).sum())
                .isEqualTo(equipment.totalEquipment());
        assertThat(equipmentByBranch).anySatisfy(value -> {
            assertThat(value.branchId()).isEqualTo(inactiveBranch);
            assertThat(value.metrics().totalEquipment()).isEqualTo(2);
        });

        IncidentReportingSummary incidents = incidentReportingQuery.summarize(organization, window);
        assertThat(incidents.totalIncidents()).isEqualTo(3);
        assertThat(incidents.openIncidents()).isEqualTo(2);
        assertThat(incidents.createdInRange()).isEqualTo(2);
        assertThat(incidents.resolvedInRange()).isEqualTo(1);
        assertThat(incidents.countsByStatus())
                .containsEntry(IncidentStatus.OPEN, 1L)
                .containsEntry(IncidentStatus.IN_PROGRESS, 1L)
                .containsEntry(IncidentStatus.RESOLVED, 1L);
        assertThat(incidents.countsByPriority())
                .containsEntry(IncidentPriority.LOW, 1L)
                .containsEntry(IncidentPriority.MEDIUM, 1L)
                .containsEntry(IncidentPriority.CRITICAL, 1L);
        var incidentByBranch = incidentReportingQuery.summarizeByBranch(bothBranches, window);
        assertThat(incidentByBranch).hasSize(2);
        assertThat(incidentByBranch.stream().mapToLong(value -> value.metrics().totalIncidents()).sum())
                .isEqualTo(incidents.totalIncidents());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.incident_status_history where incident_id = ?",
                Integer.class, resolvedIncident)).isEqualTo(2);

        var maintenance = maintenanceReportingQuery.summarize(organization, operationalDate);
        assertThat(maintenance.totalMaintenance()).isEqualTo(4);
        assertThat(maintenance.overdueScheduledMaintenance()).isEqualTo(1);
        assertThat(maintenance.countsByStatus())
                .containsEntry(MaintenanceStatus.SCHEDULED, 2L)
                .containsEntry(MaintenanceStatus.IN_PROGRESS, 1L)
                .containsEntry(MaintenanceStatus.COMPLETED, 1L)
                .containsEntry(MaintenanceStatus.CANCELLED, 0L);
        var maintenanceByBranch = maintenanceReportingQuery.summarizeByBranch(
                bothBranches, operationalDate);
        assertThat(maintenanceByBranch).hasSize(2);
        assertThat(maintenanceByBranch.stream()
                .mapToLong(value -> value.metrics().totalMaintenance()).sum())
                .isEqualTo(maintenance.totalMaintenance());
        assertThat(maintenanceByBranch.stream()
                .mapToLong(value -> value.metrics().overdueScheduledMaintenance()).sum())
                .isEqualTo(maintenance.overdueScheduledMaintenance());
        assertThat(openIncident).isNotNull();
    }

    @Test
    void durableEmailMetricsCountLogicalDeliveriesSeparatelyFromAttemptsWithoutPrivateFields() {
        UUID inactiveBranch = insertBranch("Email reporting branch");
        UUID initialClient = insertClient(INITIAL_BRANCH_ID);
        UUID secondClient = insertClient(inactiveBranch);
        Instant requestedAt = Instant.now().minusSeconds(600);
        Instant firstAttempt = requestedAt.plusSeconds(10);

        insertEmailDelivery(initialClient, INITIAL_BRANCH_ID,
                EmailDeliveryType.PAYMENT_RECEIPT, requestedAt, "PENDING", 0, null, null, 0);

        UUID sent = insertEmailDelivery(initialClient, INITIAL_BRANCH_ID,
                EmailDeliveryType.ACCESS_CREDENTIAL, requestedAt, "PENDING", 0, null, null, 0);
        insertEmailAttempt(sent, 1, "SENT", null, firstAttempt, "private-provider-id");
        finalizeDelivery(sent, "SENT", 1, null, firstAttempt.plusSeconds(2), 1);

        UUID failed = insertEmailDelivery(secondClient, inactiveBranch,
                EmailDeliveryType.ACCESS_CREDENTIAL, requestedAt, "PENDING", 0, null, null, 0);
        insertEmailAttempt(failed, 1, "FAILED", "TRANSPORT_TIMEOUT", firstAttempt, null);
        finalizeDelivery(failed, "FAILED", 1, "TRANSPORT_TIMEOUT", firstAttempt.plusSeconds(2), 1);

        UUID retried = insertEmailDelivery(secondClient, inactiveBranch,
                EmailDeliveryType.PAYMENT_RECEIPT, requestedAt, "PENDING", 0, null, null, 0);
        insertEmailAttempt(retried, 1, "FAILED", "TRANSPORT_TIMEOUT", firstAttempt, null);
        finalizeDelivery(retried, "FAILED", 1, "TRANSPORT_TIMEOUT", firstAttempt.plusSeconds(2), 1);
        Instant retryAt = firstAttempt.plusSeconds(60);
        insertEmailAttempt(retried, 2, "SENT", null, retryAt, "private-provider-id-retry");
        finalizeDelivery(retried, "SENT", 2, null, retryAt.plusSeconds(2), 2);

        UUID ambiguous = insertEmailDelivery(initialClient, INITIAL_BRANCH_ID,
                EmailDeliveryType.PAYMENT_RECEIPT, requestedAt, "PENDING", 0, null, null, 0);
        insertEmailAttempt(ambiguous, 1, "AMBIGUOUS", "AMBIGUOUS_TRANSPORT_OUTCOME",
                firstAttempt, null);
        finalizeDelivery(ambiguous, "FAILED", 1,
                "AMBIGUOUS_TRANSPORT_OUTCOME", firstAttempt.plusSeconds(2), 1);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        ReportingQueryWindow window = new ReportingQueryWindow(
                today.minusDays(1), today.plusDays(1), ZoneOffset.UTC);
        ReportingQueryScope organization = ReportingQueryScope.organization();
        ReportingQueryScope bothBranches = ReportingQueryScope.branches(
                List.of(INITIAL_BRANCH_ID, inactiveBranch));
        EmailDeliveryReportingSummary summary = emailDeliveryReportingQuery.summarize(
                organization, window);

        assertThat(summary.totalDeliveries()).isEqualTo(5);
        assertThat(summary.pendingDeliveries()).isEqualTo(1);
        assertThat(summary.sentDeliveries()).isEqualTo(2);
        assertThat(summary.failedDeliveries()).isEqualTo(2);
        assertThat(summary.retryAttempts()).isEqualTo(1);
        assertThat(summary.deliveriesByType())
                .containsEntry(EmailDeliveryType.PAYMENT_RECEIPT, 3L)
                .containsEntry(EmailDeliveryType.ACCESS_CREDENTIAL, 2L);
        assertThat(summary.failuresByCode())
                .containsEntry(EmailDeliveryFailureCode.TRANSPORT_TIMEOUT, 2L)
                .containsEntry(EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME, 1L);

        List<EmailDeliveryBranchReportingSummary> branches =
                emailDeliveryReportingQuery.summarizeByBranch(bothBranches, window);
        assertThat(branches).hasSize(2);
        assertThat(branches.stream().mapToLong(value -> value.summary().totalDeliveries()).sum())
                .isEqualTo(summary.totalDeliveries());
        assertThat(branches.stream().mapToLong(value -> value.summary().retryAttempts()).sum())
                .isEqualTo(summary.retryAttempts());
        assertThat(summary.toString())
                .doesNotContain("private-provider-id", "example.test", "recipient", "OAuth",
                        "body", "token");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.email_delivery_attempts where delivery_id = ?",
                Integer.class, retried)).isEqualTo(2);
    }

    @Test
    void existingOperationalIndexesSupportBoundedBranchAndAttemptLookups() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.execute("set local enable_seqscan = off");
            assertPlanUsesIndex("""
                    explain (analyze, buffers, costs false)
                    select id from gym.equipment
                    where branch_id = ? and status = 'OUT_OF_SERVICE'
                    """, "idx_equipment_branch_status_created_at", INITIAL_BRANCH_ID);
            assertPlanUsesIndex("""
                    explain (analyze, buffers, costs false)
                    select id from gym.incidents
                    where branch_id = ? and reported_at >= ? and reported_at < ?
                    """, "idx_incidents_branch_status_reported_at", INITIAL_BRANCH_ID);
            assertPlanUsesAnyIndex("""
                    explain (analyze, buffers, costs false)
                    select id from gym.maintenances
                    where branch_id = ? and status = 'SCHEDULED' and scheduled_on < ?
                    """, List.of(
                            "idx_maintenances_open_scheduled_on",
                            "idx_maintenances_branch_status_scheduled_on"), INITIAL_BRANCH_ID);
            assertPlanUsesAnyIndex("""
                    explain (analyze, buffers, costs false)
                    select id from gym.maintenances
                    where branch_id = ? and status = 'SCHEDULED'
                    """, List.of(
                            "idx_maintenances_open_scheduled_on",
                            "idx_maintenances_branch_status_scheduled_on"), INITIAL_BRANCH_ID);
            assertPlanUsesIndex("""
                    explain (analyze, buffers, costs false)
                    select id from gym.email_deliveries
                    where branch_id = ? and status = 'FAILED' and updated_at < ?
                    """, "idx_email_deliveries_branch_status_updated_at", INITIAL_BRANCH_ID);
            assertPlanUsesIndex("""
                    explain (analyze, buffers, costs false)
                    select attempt_number from gym.email_delivery_attempts
                    where delivery_id = ? and started_at >= ? and started_at < ?
                    """, "idx_email_delivery_attempts_delivery_started_at", UUID.randomUUID());
        });
    }

    private void assertPlanUsesIndex(String sql, String indexName, UUID id) {
        assertPlanUsesAnyIndex(sql, List.of(indexName), id);
    }

    private void assertPlanUsesAnyIndex(String sql, List<String> indexNames, UUID id) {
        List<String> plan;
        if (sql.contains("reported_at") || sql.contains("started_at")) {
            plan = jdbcTemplate.queryForList(
                    sql, String.class, id,
                    OffsetDateTime.ofInstant(Instant.parse("2099-09-01T00:00:00Z"), ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(Instant.parse("2099-10-01T00:00:00Z"), ZoneOffset.UTC));
        } else if (sql.contains("scheduled_on")) {
            plan = jdbcTemplate.queryForList(sql, String.class, id, LocalDate.of(2099, 9, 15));
        } else if (sql.contains("updated_at")) {
            plan = jdbcTemplate.queryForList(
                    sql, String.class, id, OffsetDateTime.now(ZoneOffset.UTC));
        } else {
            plan = jdbcTemplate.queryForList(sql, String.class, id);
        }
        assertThat(String.join("\n", plan))
                .containsAnyOf(indexNames.toArray(String[]::new))
                .contains("actual time=", "Buffers:");
    }

    private UUID insertBranch(String name) {
        UUID branchId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, is_initial_branch)
                select ?, organization.id, ?, ?, 'America/El_Salvador', 'ACTIVE', false
                from gym.organizations organization where organization.is_canonical
                """, branchId, "RPT-" + branchId.toString().replace("-", "")
                        .substring(0, 12).toUpperCase(java.util.Locale.ROOT), name);
        return branchId;
    }

    private UUID insertEquipment(UUID branchId, String status, String label) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.equipment
                    (id, equipment_category_id, name, status, created_by_user_id,
                     updated_by_user_id, branch_id)
                values (?, ?, ?, ?, ?, ?, ?)
                """, id, categoryId, label, status, adminId, adminId, branchId);
        return id;
    }

    private UUID insertRetiredEquipment(UUID branchId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.equipment
                    (id, equipment_category_id, name, status, retired_at,
                     retired_by_user_id, retirement_reason, created_by_user_id,
                     updated_by_user_id, branch_id)
                values (?, ?, 'Retired report fixture', 'RETIRED', current_timestamp,
                        ?, 'End of service', ?, ?, ?)
                """, id, categoryId, adminId, adminId, adminId, branchId);
        return id;
    }

    private UUID insertIncident(
            UUID equipmentId, UUID branchId, String status, String priority,
            Instant reportedAt, Instant resolvedAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.incidents
                    (id, equipment_id, status, priority, description, reported_at,
                     reported_by_user_id, resolved_at, resolved_by_user_id,
                     resolution_notes, branch_id)
                values (?, ?, ?, ?, 'Reporting-only fixture', ?, ?, ?, ?, ?, ?)
                """, id, equipmentId, status, priority, offset(reportedAt), adminId,
                resolvedAt == null ? null : offset(resolvedAt),
                resolvedAt == null ? null : adminId,
                resolvedAt == null ? null : "Resolved for reporting fixture", branchId);
        return id;
    }

    private void insertIncidentHistory(
            UUID incidentId, String before, String after, Instant occurredAt) {
        jdbcTemplate.update("""
                insert into gym.incident_status_history
                    (id, incident_id, previous_status, new_status, reason, occurred_at,
                     changed_by_user_id)
                values (?, ?, ?, ?, 'Reporting fixture transition', ?, ?)
                """, UUID.randomUUID(), incidentId, before, after, offset(occurredAt), adminId);
    }

    private void insertMaintenance(
            UUID equipmentId, UUID branchId, String status, LocalDate scheduledOn, boolean completed) {
        jdbcTemplate.update("""
                insert into gym.maintenances
                    (id, equipment_id, maintenance_type, status, scheduled_on, currency,
                     created_by_user_id, completed_at, completed_by_user_id, actions_taken,
                     branch_id)
                values (?, ?, 'PREVENTIVE', ?, ?, 'USD', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), equipmentId, status, scheduledOn, adminId,
                completed ? OffsetDateTime.now(ZoneOffset.UTC) : null,
                completed ? adminId : null,
                completed ? "Completed reporting fixture" : null, branchId);
    }

    private UUID insertClient(UUID branchId) {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().replace("-", "").substring(0, 12);
        jdbcTemplate.update("""
                insert into gym.clients
                    (id, first_name, last_name, email, phone, status, created_by_user_id,
                     home_branch_id)
                values (?, 'Reporting', 'Client', ?, ?, 'ACTIVE', ?, ?)
                """, id, "report-" + suffix + "@example.test", "+1500" + suffix.substring(0, 7),
                adminId, branchId);
        return id;
    }

    private UUID insertEmailDelivery(
            UUID clientId, UUID branchId, EmailDeliveryType type, Instant requestedAt,
            String status, int attemptCount, String failureCode, String failureMessage,
            long version) {
        UUID id = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        String contentType = type == EmailDeliveryType.PAYMENT_RECEIPT
                ? "application/pdf" : "image/png";
        String filename = type == EmailDeliveryType.PAYMENT_RECEIPT ? "receipt.pdf" : "credential.png";
        jdbcTemplate.update("""
                insert into gym.email_deliveries
                    (id, delivery_type, source_resource_id, client_id, branch_id,
                     recipient_snapshot, subject_snapshot, template_version,
                     attachment_resource_type, attachment_resource_id,
                     attachment_filename, attachment_content_type, attachment_size_bytes,
                     attachment_checksum_sha256, idempotency_key_digest, status,
                     attempt_count, last_failure_code, last_failure_message, requested_at,
                     requested_by_user_id, sent_at, last_attempt_at, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, 'private-recipient@example.test', 'private subject', 'v1',
                        ?, ?, ?, ?, 16, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, type.name(), sourceId, clientId, branchId, type.name(), attachmentId,
                filename, contentType, "a".repeat(64), UUID.randomUUID().toString().replace("-", "")
                        .repeat(2).substring(0, 64), status, attemptCount, failureCode, failureMessage,
                offset(requestedAt), adminId,
                status.equals("SENT") ? offset(requestedAt.plusSeconds(30)) : null,
                attemptCount > 0 ? offset(requestedAt.plusSeconds(30)) : null,
                offset(requestedAt), offset(requestedAt), version);
        return id;
    }

    private void insertEmailAttempt(
            UUID deliveryId, int number, String result, String failureCode,
            Instant startedAt, String providerMessageId) {
        boolean failed = result.equals("FAILED") || result.equals("AMBIGUOUS");
        jdbcTemplate.update("""
                insert into gym.email_delivery_attempts
                    (id, delivery_id, attempt_number, result, started_at, completed_at,
                     failure_code, failure_message, attempted_by_user_id, provider_message_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), deliveryId, number, result, offset(startedAt),
                offset(startedAt.plusSeconds(2)), failureCode,
                failed ? "Safe bounded failure" : null, adminId, providerMessageId);
    }

    private void finalizeDelivery(
            UUID deliveryId, String status, int count, String failureCode,
            Instant attemptedAt, long version) {
        jdbcTemplate.update("""
                update gym.email_deliveries
                set status = ?, attempt_count = ?, last_failure_code = ?,
                    last_failure_message = ?, last_attempt_at = ?, sent_at = ?, version = ?
                where id = ?
                """, status, count, failureCode,
                failureCode == null ? null : "Safe bounded failure",
                offset(attemptedAt), status.equals("SENT") ? offset(attemptedAt) : null,
                version, deliveryId);
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
