package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceStatusPolicy;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaintenanceStatusHistoryJpaEntityTest {

    private static final UUID MAINTENANCE_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-04T02:00:00Z");
    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "admin");

    @Test
    void createsInitialScheduledHistory() {
        var details = MaintenanceStatusHistoryJpaEntity
                .initial(MAINTENANCE_ID, ACTOR, NOW)
                .toDetails();

        assertThat(details.previousStatus()).isNull();
        assertThat(details.newStatus()).isEqualTo(MaintenanceStatus.SCHEDULED);
        assertThat(details.reason()).isEqualTo("Maintenance scheduled.");
        assertThat(details.changedByUserId()).isEqualTo(ACTOR_ID);
    }

    @Test
    void createsTransitionHistory() {
        var transition = new MaintenanceStatusPolicy().start(
                MaintenanceStatus.SCHEDULED, "Work started.");
        var details = MaintenanceStatusHistoryJpaEntity
                .transition(MAINTENANCE_ID, transition, ACTOR, NOW)
                .toDetails();

        assertThat(details.previousStatus()).isEqualTo(MaintenanceStatus.SCHEDULED);
        assertThat(details.newStatus()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
        assertThat(details.reason()).isEqualTo("Work started.");
    }
}
