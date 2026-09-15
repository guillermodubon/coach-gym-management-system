package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialIssued;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialReplaced;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialRevoked;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AccessCredentialAuditEntryJpaEntityTest {

    private static final UUID CREDENTIAL_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000006101");
    private static final UUID REPLACEMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000006102");
    private static final UUID CLIENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000006103");
    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000006104");
    private static final Instant NOW = Instant.parse("2026-09-13T17:10:00Z");

    @Test
    void mapsIssuedCredentialWithActorResourceAndSafeMetadata() {
        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(
                new AccessCredentialIssued(
                        CREDENTIAL_ID, CLIENT_ID, "AC-ISSUED", "v1", "sha256-v1",
                        ACTOR_ID, "coach-admin", NOW));

        assertCommonFields(entry, "ACCESS_CREDENTIAL_ISSUED", CREDENTIAL_ID,
                "AC-ISSUED", "Client access credential issued.");
        assertThat(metadata(entry)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "clientId", CLIENT_ID.toString(),
                "newStatus", "ACTIVE",
                "payloadVersion", "v1",
                "tokenSchemeVersion", "sha256-v1"));
    }

    @Test
    void mapsRevokedCredentialWithTransitionAndReasonPresenceOnly() {
        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(
                new AccessCredentialRevoked(
                        CREDENTIAL_ID, CLIENT_ID, "AC-REVOKED",
                        AccessCredentialStatus.ACTIVE, AccessCredentialStatus.REVOKED,
                        ACTOR_ID, "coach-admin", NOW, true));

        assertCommonFields(entry, "ACCESS_CREDENTIAL_REVOKED", CREDENTIAL_ID,
                "AC-REVOKED", "Client access credential revoked.");
        assertThat(metadata(entry)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "clientId", CLIENT_ID.toString(),
                "previousStatus", "ACTIVE",
                "newStatus", "REVOKED",
                "reasonPresent", true));
    }

    @Test
    void mapsReplacementWithCorrelationToNewCredential() {
        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(
                new AccessCredentialReplaced(
                        CREDENTIAL_ID, REPLACEMENT_ID, CLIENT_ID,
                        AccessCredentialStatus.REVOKED, AccessCredentialStatus.ACTIVE,
                        ACTOR_ID, "coach-admin", NOW, false));

        assertCommonFields(entry, "ACCESS_CREDENTIAL_REPLACED", CREDENTIAL_ID,
                null, "Client access credential replaced.");
        Map<String, Object> metadata = metadata(entry);
        assertThat(metadata).containsExactlyInAnyOrderEntriesOf(Map.of(
                "clientId", CLIENT_ID.toString(),
                "previousStatus", "REVOKED",
                "newStatus", "ACTIVE",
                "replacementCredentialId", REPLACEMENT_ID.toString(),
                "reasonPresent", false));
        assertThat(metadata.keySet()).isSubsetOf(Set.of(
                "clientId", "previousStatus", "newStatus",
                "replacementCredentialId", "reasonPresent"));
        assertThat(metadata.toString()).doesNotContain(
                "rawToken", "qrPayload", "digest", "png", "storage",
                "client@example.com", "+50370000000", "sk_test_");
    }

    private static void assertCommonFields(
            AuditEntryJpaEntity entry,
            String actionCode,
            UUID resourceId,
            String resourceCode,
            String summary) {
        assertThat(field(entry, "actionCode")).isEqualTo(actionCode);
        assertThat(field(entry, "resourceType")).isEqualTo("ACCESS_CREDENTIAL");
        assertThat(field(entry, "resourceId")).isEqualTo(resourceId);
        assertThat(field(entry, "resourceCodeSnapshot")).isEqualTo(resourceCode);
        assertThat(field(entry, "actorUserId")).isEqualTo(ACTOR_ID);
        assertThat(field(entry, "actorIdentifierSnapshot")).isEqualTo("coach-admin");
        assertThat(field(entry, "occurredAt")).isEqualTo(NOW);
        assertThat(field(entry, "summary")).isEqualTo(summary);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(AuditEntryJpaEntity entry) {
        return (Map<String, Object>) ReflectionTestUtils.getField(entry, "metadata");
    }

    private static Object field(AuditEntryJpaEntity entry, String fieldName) {
        return ReflectionTestUtils.getField(entry, fieldName);
    }
}
