package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.access.AccessAttemptRecorded;
import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessResult;
import java.time.Instant;
import java.util.UUID;

import io.github.guillermodubon.coachgym.audit.application.AccessAuditEventListener;
import io.github.guillermodubon.coachgym.audit.application.AuditEntryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccessAuditEventListenerTest {

    private static final UUID ACCESS_RECORD_ID =
            UUID.fromString(
                    "40000000-0000-0000-0000-000000000001");

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_USER_ID =
            UUID.fromString(
                    "50000000-0000-0000-0000-000000000001");

    private static final Instant OCCURRED_AT =
            Instant.parse(
                    "2026-09-15T20:00:00Z");

    private AuditEntryStore auditEntryStore;
    private AccessAuditEventListener listener;

    @BeforeEach
    void setUp() {
        auditEntryStore =
                mock(AuditEntryStore.class);

        listener =
                new AccessAuditEventListener(
                        auditEntryStore);
    }

    @Test
    void recordsDeniedAccessAttempt() {
        AccessAttemptRecorded event =
                deniedEvent();

        listener.record(event);

        verify(auditEntryStore)
                .recordDeniedAccessAttempt(event);
    }

    @Test
    void doesNotRecordAllowedAccessAttempt() {
        AccessAttemptRecorded event =
                allowedEvent();

        listener.record(event);

        verify(auditEntryStore, never())
                .recordDeniedAccessAttempt(event);
    }

    @Test
    void recordsDeniedAttemptForUnknownIdentifier() {
        AccessAttemptRecorded event =
                unresolvedIdentifierEvent();

        listener.record(event);

        verify(auditEntryStore)
                .recordDeniedAccessAttempt(event);
    }

    private static AccessAttemptRecorded deniedEvent() {
        return new AccessAttemptRecorded(
                ACCESS_RECORD_ID,
                "MEM-000001",
                "MEMBERSHIP_CODE",
                CLIENT_ID,
                "CLI-000001",
                MEMBERSHIP_ID,
                "MEM-000001",
                AccessResult.DENIED,
                AccessReasonCode.MEMBERSHIP_FROZEN,
                OCCURRED_AT,
                ACTOR_USER_ID,
                "receptionist",
                OCCURRED_AT);
    }

    private static AccessAttemptRecorded allowedEvent() {
        return new AccessAttemptRecorded(
                ACCESS_RECORD_ID,
                "MEM-000001",
                "MEMBERSHIP_CODE",
                CLIENT_ID,
                "CLI-000001",
                MEMBERSHIP_ID,
                "MEM-000001",
                AccessResult.ALLOWED,
                AccessReasonCode.ACCESS_ALLOWED,
                OCCURRED_AT,
                ACTOR_USER_ID,
                "receptionist",
                OCCURRED_AT);
    }

    private static AccessAttemptRecorded unresolvedIdentifierEvent() {
        return new AccessAttemptRecorded(
                ACCESS_RECORD_ID,
                "XYZ-999999",
                "UNKNOWN",
                null,
                null,
                null,
                null,
                AccessResult.DENIED,
                AccessReasonCode.IDENTIFIER_NOT_FOUND,
                OCCURRED_AT,
                ACTOR_USER_ID,
                "receptionist",
                OCCURRED_AT);
    }
}
