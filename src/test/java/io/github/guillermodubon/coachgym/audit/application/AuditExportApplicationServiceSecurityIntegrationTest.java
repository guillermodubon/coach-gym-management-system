package io.github.guillermodubon.coachgym.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.audit.AuditExportActor;
import io.github.guillermodubon.coachgym.audit.AuditExportQuery;
import java.time.Instant;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

class AuditExportApplicationServiceSecurityIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Autowired
    private AuditExportApplicationService auditExportService;

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void receptionistIsDeniedBeforeTheExportQueryRuns() {
        assertThatThrownBy(() -> auditExportService.export(
                query(),
                new AuditExportActor(adminId, ADMIN_USERNAME),
                row -> { }))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanRunAnEmptyExportAndReceivesStableResultMetadata() {
        List<Object> rows = new ArrayList<>();

        var result = auditExportService.export(
                query(),
                new AuditExportActor(adminId, ADMIN_USERNAME),
                ignored -> rows.add(ignored));

        assertThat(rows).isEmpty();
        assertThat(result.rowsExported()).isZero();
        assertThat(result.filename()).matches("audit-export-\\d{8}-\\d{6}Z\\.csv");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void branchAdministratorCanExportOnlyAnExplicitActiveAssignment() {
        String username = "audit-export-branch-" + UUID.randomUUID();
        UUID branchAdminId = provisionUser(
                username, username + "@example.test", "Safe-Test-Password-123!", "ADMIN");
        jdbcTemplate.update(
                "update gym.staff_scopes set scope_type='BRANCH', version=version+1 where user_id=?",
                branchAdminId);
        UUID assignedBranchId = UUID.fromString("7b0bf7d5-5184-43d2-8f9a-200000000002");

        var result = auditExportService.export(
                branchQuery(assignedBranchId),
                new AuditExportActor(branchAdminId, username),
                ignored -> { });

        assertThat(result.rowsExported()).isZero();
        assertThatThrownBy(() -> auditExportService.export(
                branchQuery(UUID.randomUUID()),
                new AuditExportActor(branchAdminId, username),
                ignored -> { }))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static AuditExportQuery query() {
        return new AuditExportQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:01:00Z"),
                null,
                null);
    }

    private static AuditExportQuery branchQuery(UUID branchId) {
        return AuditExportQuery.from(
                null, null, null, null, null, null, null, null,
                Set.of(branchId),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:01:00Z"),
                null, null);
    }
}
