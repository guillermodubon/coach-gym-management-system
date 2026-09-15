package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.maintenance.IncidentInvestigationStartedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriorityChangedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentResolvedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncidentAuditEntryJpaEntityTest {
    private static final UUID INCIDENT_ID = UUID.randomUUID();
    private static final UUID EQUIPMENT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-03T02:00:00Z");

    @Test void mapsReportedEventWithSafeMetadata() {
        var entity = AuditEntryJpaEntity.from(new IncidentReportedEvent(INCIDENT_ID, "INC-000001", EQUIPMENT_ID, "EQP-000001", IncidentPriority.CRITICAL, true, ACTOR_ID, "admin", NOW));
        assertThat(entity.actionCode()).isEqualTo("INCIDENT_REPORTED");
        assertThat(entity.resourceType()).isEqualTo("INCIDENT");
        assertThat(entity.resourceId()).isEqualTo(INCIDENT_ID);
        assertThat(entity.resourceCodeSnapshot()).isEqualTo("INC-000001");
        assertThat(entity.metadata()).containsEntry("priority", "CRITICAL").containsEntry("takenOutOfService", true).doesNotContainKeys("description", "resolutionNotes", "reason");
    }

    @Test void mapsLifecycleEvents() {
        assertThat(AuditEntryJpaEntity.from(new IncidentInvestigationStartedEvent(INCIDENT_ID, "INC-000001", EQUIPMENT_ID, ACTOR_ID, "admin", NOW)).actionCode()).isEqualTo("INCIDENT_INVESTIGATION_STARTED");
        var changed = AuditEntryJpaEntity.from(new IncidentPriorityChangedEvent(INCIDENT_ID, "INC-000001", IncidentPriority.HIGH, IncidentPriority.CRITICAL, "Risk.", ACTOR_ID, "admin", NOW));
        assertThat(changed.actionCode()).isEqualTo("INCIDENT_PRIORITY_CHANGED");
        assertThat(changed.metadata()).containsEntry("previousPriority", "HIGH").containsEntry("newPriority", "CRITICAL").doesNotContainKey("reason");
        var resolved = AuditEntryJpaEntity.from(new IncidentResolvedEvent(INCIDENT_ID, "INC-000001", EQUIPMENT_ID, "Sensitive repair notes.", ACTOR_ID, "admin", NOW));
        assertThat(resolved.actionCode()).isEqualTo("INCIDENT_RESOLVED");
        assertThat(resolved.metadata()).doesNotContainKey("resolutionNotes");
    }
}
