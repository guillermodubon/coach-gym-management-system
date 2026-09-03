package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.maintenance.IncidentInvestigationStartedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriorityChangedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentResolvedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentAuditEventListenerTest {
    private static final UUID INCIDENT_ID = UUID.randomUUID();
    private static final UUID EQUIPMENT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-03T02:00:00Z");
    @Mock private AuditEntryStore store;

    @Test void delegatesAllIncidentEvents() {
        IncidentAuditEventListener listener = new IncidentAuditEventListener(store);
        var reported = new IncidentReportedEvent(INCIDENT_ID, "INC-000001", EQUIPMENT_ID, "EQP-000001", IncidentPriority.HIGH, true, ACTOR_ID, "admin", NOW);
        var started = new IncidentInvestigationStartedEvent(INCIDENT_ID, "INC-000001", EQUIPMENT_ID, ACTOR_ID, "admin", NOW);
        var priority = new IncidentPriorityChangedEvent(INCIDENT_ID, "INC-000001", IncidentPriority.HIGH, IncidentPriority.CRITICAL, "Risk increased.", ACTOR_ID, "admin", NOW);
        var resolved = new IncidentResolvedEvent(INCIDENT_ID, "INC-000001", EQUIPMENT_ID, "Resolved.", ACTOR_ID, "admin", NOW);
        listener.record(reported); listener.record(started); listener.record(priority); listener.record(resolved);
        verify(store).recordIncidentReported(reported);
        verify(store).recordIncidentInvestigationStarted(started);
        verify(store).recordIncidentPriorityChanged(priority);
        verify(store).recordIncidentResolved(resolved);
    }
}
