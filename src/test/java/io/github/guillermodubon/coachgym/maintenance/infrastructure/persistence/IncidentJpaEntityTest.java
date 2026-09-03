package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentDefinition;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentStatusPolicy;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncidentJpaEntityTest {

    private static final UUID INCIDENT_ID = UUID.randomUUID();
    private static final UUID EQUIPMENT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "admin");

    @Test
    void reportsOpenIncident() {
        IncidentJpaEntity entity = IncidentJpaEntity.report(
                INCIDENT_ID,
                new IncidentDefinition(
                        EQUIPMENT_ID, IncidentPriority.HIGH,
                        "Drive belt failure.", false, null),
                ACTOR,
                NOW);

        assertThat(entity.id()).isEqualTo(INCIDENT_ID);
        assertThat(entity.equipmentId()).isEqualTo(EQUIPMENT_ID);
        assertThat(entity.status()).isEqualTo(IncidentStatus.OPEN);
        assertThat(entity.priority()).isEqualTo(IncidentPriority.HIGH);
        assertThat(entity.reportedByUserId()).isEqualTo(ACTOR_ID);
        assertThat(entity.reportedAt()).isEqualTo(NOW);
        assertThat(entity.version()).isZero();
    }

    @Test
    void resolvesIncidentAtomicallyInEntityState() {
        IncidentJpaEntity entity = openEntity();
        entity.applyTransition(
                new IncidentStatusPolicy().startInvestigation(
                        IncidentStatus.OPEN, "Started."),
                null,
                ACTOR,
                NOW.plusSeconds(60));
        entity.applyTransition(
                new IncidentStatusPolicy().resolve(
                        IncidentStatus.IN_PROGRESS, "Resolved."),
                " Controller replaced. ",
                ACTOR,
                NOW.plusSeconds(120));

        assertThat(entity.status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(entity.resolvedAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(entity.resolvedByUserId()).isEqualTo(ACTOR_ID);
        assertThat(entity.resolutionNotes()).isEqualTo("Controller replaced.");
    }

    @Test
    void rejectsResolutionWithoutNotes() {
        IncidentJpaEntity entity = openEntity();
        entity.applyTransition(
                new IncidentStatusPolicy().startInvestigation(
                        IncidentStatus.OPEN, "Started."),
                null,
                ACTOR,
                NOW.plusSeconds(60));

        assertThatThrownBy(() -> entity.applyTransition(
                new IncidentStatusPolicy().resolve(
                        IncidentStatus.IN_PROGRESS, "Resolved."),
                " ",
                ACTOR,
                NOW.plusSeconds(120)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Resolution notes are required.");
    }

    @Test
    void changesPriority() {
        IncidentJpaEntity entity = openEntity();
        entity.changePriority(IncidentPriority.CRITICAL, NOW.plusSeconds(10));
        assertThat(entity.priority()).isEqualTo(IncidentPriority.CRITICAL);
        assertThat(entity.updatedAt()).isEqualTo(NOW.plusSeconds(10));
    }

    private static IncidentJpaEntity openEntity() {
        return IncidentJpaEntity.report(
                INCIDENT_ID,
                new IncidentDefinition(
                        EQUIPMENT_ID, IncidentPriority.HIGH,
                        "Drive belt failure.", false, null),
                ACTOR,
                NOW);
    }
}
