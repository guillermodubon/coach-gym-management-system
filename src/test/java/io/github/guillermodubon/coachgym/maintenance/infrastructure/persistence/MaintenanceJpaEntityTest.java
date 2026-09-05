package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.EquipmentMaintenanceOutcome;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceType;
import io.github.guillermodubon.coachgym.maintenance.application.MaintenanceStateConflictException;
import io.github.guillermodubon.coachgym.maintenance.application.MaintenanceVersionConflictException;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceCompletion;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceDefinition;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceStatusPolicy;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaintenanceJpaEntityTest {

    private static final UUID EQUIPMENT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "admin");
    private static final Instant NOW = Instant.parse("2026-09-04T02:00:00Z");

    @Test
    void schedulesEntityWithServerControlledInitialState() {
        MaintenanceJpaEntity entity = MaintenanceJpaEntity.schedule(
                definition(), ACTOR, NOW);

        assertThat(entity.id()).isNotNull();
        assertThat(entity.equipmentId()).isEqualTo(EQUIPMENT_ID);
        assertThat(entity.status()).isEqualTo(MaintenanceStatus.SCHEDULED);
        assertThat(entity.version()).isZero();
    }

    @Test
    void startsAndCompletesEntity() {
        MaintenanceJpaEntity entity = MaintenanceJpaEntity.schedule(
                definition(), ACTOR, NOW);
        MaintenanceStatusPolicy policy = new MaintenanceStatusPolicy();
        entity.transition(
                0L,
                policy.start(MaintenanceStatus.SCHEDULED, "Started."),
                NOW.plusSeconds(60),
                NOW.plusSeconds(60));

        MaintenanceCompletion completion = new MaintenanceCompletion(
                NOW.plusSeconds(120),
                "Replaced belt.",
                new BigDecimal("25.00"),
                "USD",
                EquipmentMaintenanceOutcome.AVAILABLE);
        entity.complete(
                0L,
                policy.complete(MaintenanceStatus.IN_PROGRESS, "Completed."),
                completion,
                ACTOR,
                NOW.plusSeconds(120));

        assertThat(entity.status()).isEqualTo(MaintenanceStatus.COMPLETED);
        assertThat(entity.startedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(entity.completedAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void rejectsStaleVersionAndWrongState() {
        MaintenanceJpaEntity entity = MaintenanceJpaEntity.schedule(
                definition(), ACTOR, NOW);
        assertThatThrownBy(() -> entity.updateScheduled(
                9L,
                new io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceUpdateDefinition(
                        LocalDate.now(), null, null, null, "USD", null, null),
                NOW))
                .isInstanceOf(MaintenanceVersionConflictException.class);

        entity.transition(
                0L,
                new MaintenanceStatusPolicy().start(
                        MaintenanceStatus.SCHEDULED, "Started."),
                NOW,
                NOW);
        assertThatThrownBy(() -> entity.updateScheduled(
                0L,
                new io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceUpdateDefinition(
                        LocalDate.now(), null, null, null, "USD", null, null),
                NOW))
                .isInstanceOf(MaintenanceStateConflictException.class);
    }

    private static MaintenanceDefinition definition() {
        return new MaintenanceDefinition(
                EQUIPMENT_ID,
                null,
                MaintenanceType.PREVENTIVE,
                LocalDate.of(2026, 9, 10),
                null,
                null,
                new BigDecimal("20.00"),
                "USD",
                null,
                null);
    }
}
