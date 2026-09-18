package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.audit.AuditExportCompleted;
import io.github.guillermodubon.coachgym.audit.AuditSortDirection;
import io.github.guillermodubon.coachgym.audit.AuditSortField;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditExportAuditEventListenerTest {

    @Test
    void recordsCompletedExportThroughTheExistingAuditStore() {
        AuditEntryStore store = mock(AuditEntryStore.class);
        AuditExportAuditEventListener listener =
                new AuditExportAuditEventListener(store);
        AuditExportCompleted event = new AuditExportCompleted(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "admin.user",
                Instant.parse("2026-09-17T00:00:00Z"),
                Instant.parse("2026-09-17T01:00:00Z"),
                "occurredFrom,occurredUntil",
                AuditSortField.OCCURRED_AT,
                AuditSortDirection.DESC,
                0,
                10_000,
                AuditExportCompleted.FORMAT_CSV,
                Instant.parse("2026-09-17T02:00:00Z"));

        listener.record(event);

        verify(store).recordAuditExportCompleted(event);
    }
}
