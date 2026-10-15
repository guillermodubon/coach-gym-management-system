package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialReplaced;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccessCredentialAuditPersistenceAdapterTest {

    @Mock
    private AuditEntryJpaRepository repository;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void persistsReplacementThroughTheExistingAuditRepository() {
        UUID previousId = UUID.fromString("00000000-0000-0000-0000-000000006201");
        UUID replacementId = UUID.fromString("00000000-0000-0000-0000-000000006202");
        UUID clientId = UUID.fromString("00000000-0000-0000-0000-000000006203");
        UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000006204");
        AccessCredentialReplaced event = new AccessCredentialReplaced(
                previousId, replacementId, clientId,
                AccessCredentialStatus.REVOKED, AccessCredentialStatus.ACTIVE,
                actorId, "coach-admin", Instant.parse("2026-09-13T17:20:00Z"), true);
        AuditEntryPersistenceAdapter adapter = new AuditEntryPersistenceAdapter(
                repository, jdbcTemplate);

        adapter.recordAccessCredentialReplaced(event);

        ArgumentCaptor<AuditEntryJpaEntity> captor =
                ArgumentCaptor.forClass(AuditEntryJpaEntity.class);
        verify(repository).save(captor.capture());
        AuditEntryJpaEntity entry = captor.getValue();
        assertThat(field(entry, "actionCode")).isEqualTo("ACCESS_CREDENTIAL_REPLACED");
        assertThat(field(entry, "resourceType")).isEqualTo("ACCESS_CREDENTIAL");
        assertThat(field(entry, "resourceId")).isEqualTo(previousId);
        assertThat(metadata(entry)).containsEntry(
                "replacementCredentialId", replacementId.toString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(AuditEntryJpaEntity entry) {
        return (Map<String, Object>) ReflectionTestUtils.getField(entry, "metadata");
    }

    private static Object field(AuditEntryJpaEntity entry, String fieldName) {
        return ReflectionTestUtils.getField(entry, fieldName);
    }
}
