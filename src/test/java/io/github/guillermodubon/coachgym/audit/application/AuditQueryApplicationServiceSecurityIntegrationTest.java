package io.github.guillermodubon.coachgym.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.audit.AuditEntryPage;
import io.github.guillermodubon.coachgym.audit.AuditQueryPolicy;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

class AuditQueryApplicationServiceSecurityIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Autowired
    private AuditQueryApplicationService auditQueryService;

    private UUID entryId;

    @BeforeEach
    void insertSafeAuditFixture() {
        entryId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.audit_entries
                    (id, actor_user_id, actor_identifier_snapshot,
                     action_code, resource_type, resource_id,
                     resource_code_snapshot, summary, metadata)
                values (?, ?, ?, 'CLIENT_REGISTERED', 'CLIENT', ?, ?, ?,
                        '{"password":"must-not-return"}'::jsonb)
                """,
                entryId,
                adminId,
                ADMIN_USERNAME,
                UUID.randomUUID(),
                "CLI-AUDIT-001",
                "Client registered for service security test");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListAuditEntriesThroughTheApplicationService() {
        AuditEntryPage page = auditQueryService.findAll(null);

        assertThat(page.items())
                .extracting(item -> item.id())
                .contains(entryId);
        assertThat(page.size()).isEqualTo(AuditQueryPolicy.DEFAULT_SIZE);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanRetrieveSanitizedAuditDetailsThroughTheApplicationService() {
        var details = auditQueryService.findById(entryId);

        assertThat(details.id()).isEqualTo(entryId);
        assertThat(details.metadata().values()).isEmpty();
        assertThat(details.metadata().metadataRedacted()).isTrue();
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void receptionistIsDeniedBeforeTheAuditQueryRuns() {
        assertThatThrownBy(() -> auditQueryService.findAll(null))
                .isInstanceOf(AccessDeniedException.class);
    }
}
