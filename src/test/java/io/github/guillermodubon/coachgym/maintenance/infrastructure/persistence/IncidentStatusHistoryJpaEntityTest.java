package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncidentStatusHistoryJpaEntityTest {

    private static final UUID INCIDENT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    @Test
    void createsInitialOpenHistory() {
        IncidentStatusHistoryJpaEntity history =
                IncidentStatusHistoryJpaEntity.initial(
                        INCIDENT_ID, ACTOR_ID, NOW);

        assertThat(history.previousStatus()).isNull();
        assertThat(history.newStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(history.reason()).isEqualTo("Incident reported.");
        assertThat(history.changedByUserId()).isEqualTo(ACTOR_ID);
    }

    @Test
    void createsNormalizedTransitionHistory() {
        IncidentStatusHistoryJpaEntity history =
                IncidentStatusHistoryJpaEntity.transition(
                        INCIDENT_ID,
                        IncidentStatus.OPEN,
                        IncidentStatus.IN_PROGRESS,
                        "  Investigation started.  ",
                        ACTOR_ID,
                        NOW);

        assertThat(history.reason()).isEqualTo("Investigation started.");
    }

    @Test
    void rejectsSameStatusHistory() {
        assertThatThrownBy(() ->
                IncidentStatusHistoryJpaEntity.transition(
                        INCIDENT_ID,
                        IncidentStatus.OPEN,
                        IncidentStatus.OPEN,
                        "No change.",
                        ACTOR_ID,
                        NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Incident history must represent a state change.");
    }
}
