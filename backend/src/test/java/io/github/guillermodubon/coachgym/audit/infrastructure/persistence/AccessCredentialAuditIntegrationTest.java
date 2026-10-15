package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialIssued;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialReplaced;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialRevoked;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

class AccessCredentialAuditIntegrationTest extends AbstractIncidentApiIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    void persistsCredentialLifecycleAuditWithSafeJsonbMetadata() {
        UUID credentialId = UUID.randomUUID();
        UUID replacementId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-13T17:30:00Z");

        eventPublisher.publishEvent(new AccessCredentialIssued(
                credentialId, clientId, "AC-INTEGRATION", "v1", "sha256-v1",
                adminId, ADMIN_USERNAME, occurredAt));
        eventPublisher.publishEvent(new AccessCredentialRevoked(
                credentialId, clientId, "AC-INTEGRATION",
                AccessCredentialStatus.ACTIVE, AccessCredentialStatus.REVOKED,
                adminId, ADMIN_USERNAME, occurredAt.plusSeconds(1), true));
        eventPublisher.publishEvent(new AccessCredentialReplaced(
                credentialId, replacementId, clientId,
                AccessCredentialStatus.REVOKED, AccessCredentialStatus.ACTIVE,
                adminId, ADMIN_USERNAME, occurredAt.plusSeconds(2), false));

        Map<String, Object> issued = auditRow("ACCESS_CREDENTIAL_ISSUED", credentialId);
        assertThat(issued).containsEntry("resource_type", "ACCESS_CREDENTIAL")
                .containsEntry("actor_user_id", adminId)
                .containsEntry("actor_identifier_snapshot", ADMIN_USERNAME)
                .containsEntry("client_id", clientId.toString())
                .containsEntry("new_status", "ACTIVE")
                .containsEntry("payload_version", "v1")
                .containsEntry("token_scheme_version", "sha256-v1");

        Map<String, Object> revoked = auditRow("ACCESS_CREDENTIAL_REVOKED", credentialId);
        assertThat(revoked).containsEntry("previous_status", "ACTIVE")
                .containsEntry("new_status", "REVOKED")
                .containsEntry("reason_present", "true");

        Map<String, Object> replaced = auditRow("ACCESS_CREDENTIAL_REPLACED", credentialId);
        assertThat(replaced).containsEntry("replacement_credential_id", replacementId.toString())
                .containsEntry("reason_present", "false");
        assertThat(replaced.get("metadata").toString()).doesNotContain(
                "rawToken", "qrPayload", "digest", "png", "storage",
                "client@example.com", "+50370000000", "sk_test_");
    }

    private Map<String, Object> auditRow(String actionCode, UUID resourceId) {
        return jdbcTemplate.queryForMap("""
                select resource_type, actor_user_id,
                       actor_identifier_snapshot,
                       metadata ->> 'clientId' as client_id,
                       metadata ->> 'newStatus' as new_status,
                       metadata ->> 'previousStatus' as previous_status,
                       metadata ->> 'payloadVersion' as payload_version,
                       metadata ->> 'tokenSchemeVersion' as token_scheme_version,
                       metadata ->> 'reasonPresent' as reason_present,
                       metadata ->> 'replacementCredentialId' as replacement_credential_id,
                       metadata::text as metadata
                from gym.audit_entries
                where action_code = ? and resource_id = ?
                """, actionCode, resourceId);
    }
}
