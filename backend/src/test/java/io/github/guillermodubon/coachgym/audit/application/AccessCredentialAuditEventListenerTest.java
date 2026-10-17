package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialIssued;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialReplaced;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialRevoked;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessCredentialAuditEventListenerTest {

    private static final UUID CREDENTIAL_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000006001");
    private static final UUID REPLACEMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000006002");
    private static final UUID CLIENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000006003");
    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000006004");
    private static final Instant NOW = Instant.parse("2026-09-13T17:00:00Z");

    @Mock
    private AuditEntryStore auditEntryStore;

    private AccessCredentialAuditEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AccessCredentialAuditEventListener(auditEntryStore);
    }

    @Test
    void forwardsIssuedEventToAuditStore() {
        AccessCredentialIssued event = new AccessCredentialIssued(
                CREDENTIAL_ID, CLIENT_ID, "AC-ISSUED", "v1", "sha256-v1",
                ACTOR_ID, "coach-admin", NOW);

        listener.record(event);

        verify(auditEntryStore).recordAccessCredentialIssued(event);
    }

    @Test
    void forwardsRevokedEventToAuditStore() {
        AccessCredentialRevoked event = new AccessCredentialRevoked(
                CREDENTIAL_ID, CLIENT_ID, "AC-REVOKED",
                AccessCredentialStatus.ACTIVE, AccessCredentialStatus.REVOKED,
                ACTOR_ID, "coach-admin", NOW, true);

        listener.record(event);

        verify(auditEntryStore).recordAccessCredentialRevoked(event);
    }

    @Test
    void forwardsReplacedEventToAuditStore() {
        AccessCredentialReplaced event = new AccessCredentialReplaced(
                CREDENTIAL_ID, REPLACEMENT_ID, CLIENT_ID,
                AccessCredentialStatus.REVOKED, AccessCredentialStatus.ACTIVE,
                ACTOR_ID, "coach-admin", NOW, true);

        listener.record(event);

        verify(auditEntryStore).recordAccessCredentialReplaced(event);
    }
}
