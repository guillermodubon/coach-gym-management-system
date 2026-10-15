package io.github.guillermodubon.coachgym.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncidentEventTest {
    private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EQUIPMENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-02T18:00:00Z");
    @Test void createsReportedEvent() {
        var event = new IncidentReportedEvent(INCIDENT_ID, " INC-000001 ", EQUIPMENT_ID, " EQP-000001 ", IncidentPriority.CRITICAL, true, ACTOR_ID, " admin ", OCCURRED_AT);
        assertThat(event.incidentCode()).isEqualTo("INC-000001");
        assertThat(event.actorIdentifier()).isEqualTo("admin");
    }
    @Test void createsPriorityChangedEvent() {
        var event = new IncidentPriorityChangedEvent(INCIDENT_ID, "INC-000001", IncidentPriority.HIGH, IncidentPriority.CRITICAL, " Risk. ", ACTOR_ID, "admin", OCCURRED_AT);
        assertThat(event.reason()).isEqualTo("Risk.");
    }
    @Test void rejectsRepeatedPriority() {
        assertThatThrownBy(() -> new IncidentPriorityChangedEvent(INCIDENT_ID, "INC-000001", IncidentPriority.HIGH, IncidentPriority.HIGH, "No change.", ACTOR_ID, "admin", OCCURRED_AT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Previous and new priorities must differ.");
    }
    @Test void createsResolvedEvent() {
        var event = new IncidentResolvedEvent(INCIDENT_ID, "INC-000001", EQUIPMENT_ID, " Resolved. ", ACTOR_ID, "admin", OCCURRED_AT);
        assertThat(event.resolutionNotes()).isEqualTo("Resolved.");
    }
}
