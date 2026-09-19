package io.github.guillermodubon.coachgym.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.audit.AuditExportActor;
import io.github.guillermodubon.coachgym.audit.AuditExportQuery;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
}
