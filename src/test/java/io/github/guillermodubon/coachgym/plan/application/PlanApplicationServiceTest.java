package io.github.guillermodubon.coachgym.plan.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.plan.DurationUnit;
import io.github.guillermodubon.coachgym.plan.PlanChangeType;
import io.github.guillermodubon.coachgym.plan.PlanChanged;
import io.github.guillermodubon.coachgym.plan.PlanDetails;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PlanApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T16:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final UUID PLAN_ID =
            UUID.fromString("2d7dc207-d3aa-4a99-840d-e3406a335040");

    private static final UUID ACTOR_ID =
            UUID.fromString("a31031de-d7c7-4887-bcae-749afea5b94c");

    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "coach-admin");

    @Mock
    private PlanStore planStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PlanApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PlanApplicationService(planStore, eventPublisher, CLOCK);
    }

    @Test
    void deactivatesAnActivePlanAndPublishesAnEvent() {
        PlanDetails activePlan = plan(true, 3);
        PlanDetails inactivePlan = plan(false, 4);

        when(planStore.findById(PLAN_ID)).thenReturn(Optional.of(activePlan));
        when(planStore.changeActive(PLAN_ID, false, 3, ACTOR, NOW))
                .thenReturn(inactivePlan);

        PlanDetails result = service.deactivate(PLAN_ID, 3, ACTOR);

        assertThat(result.active()).isFalse();
        assertThat(result.version()).isEqualTo(4);

        verify(planStore).changeActive(PLAN_ID, false, 3, ACTOR, NOW);

        ArgumentCaptor<PlanChanged> eventCaptor =
                ArgumentCaptor.forClass(PlanChanged.class);

        verify(eventPublisher).publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getValue())
                .satisfies(event -> {
                    assertThat(event.planId()).isEqualTo(PLAN_ID);
                    assertThat(event.planCode()).isEqualTo("PLAN-000001");
                    assertThat(event.changeType()).isEqualTo(PlanChangeType.DEACTIVATED);
                    assertThat(event.actorUserId()).isEqualTo(ACTOR_ID);
                    assertThat(event.actorIdentifier()).isEqualTo("coach-admin");
                    assertThat(event.occurredAt()).isEqualTo(NOW);
                });
    }

    @Test
    void reactivatesAnInactivePlanAndPublishesAnEvent() {
        PlanDetails inactivePlan = plan(false, 4);
        PlanDetails activePlan = plan(true, 5);

        when(planStore.findById(PLAN_ID)).thenReturn(Optional.of(inactivePlan));
        when(planStore.changeActive(PLAN_ID, true, 4, ACTOR, NOW))
                .thenReturn(activePlan);

        PlanDetails result = service.activate(PLAN_ID, 4, ACTOR);

        assertThat(result.active()).isTrue();
        assertThat(result.version()).isEqualTo(5);

        verify(planStore).changeActive(PLAN_ID, true, 4, ACTOR, NOW);

        ArgumentCaptor<PlanChanged> eventCaptor =
                ArgumentCaptor.forClass(PlanChanged.class);

        verify(eventPublisher).publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getValue().changeType())
                .isEqualTo(PlanChangeType.REACTIVATED);
    }

    @Test
    void rejectsDeactivatingAnAlreadyInactivePlan() {
        when(planStore.findById(PLAN_ID)).thenReturn(Optional.of(plan(false, 2)));

        assertThatThrownBy(() -> service.deactivate(PLAN_ID, 2, ACTOR))
                .isInstanceOf(PlanStateConflictException.class)
                .hasMessage("Plan is already inactive.");

        verify(planStore, never())
                .changeActive(PLAN_ID, false, 2, ACTOR, NOW);

        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsActivatingAnAlreadyActivePlan() {
        when(planStore.findById(PLAN_ID)).thenReturn(Optional.of(plan(true, 2)));

        assertThatThrownBy(() -> service.activate(PLAN_ID, 2, ACTOR))
                .isInstanceOf(PlanStateConflictException.class)
                .hasMessage("Plan is already active.");

        verify(planStore, never())
                .changeActive(PLAN_ID, true, 2, ACTOR, NOW);

        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsChangingStateWithAStaleVersion() {
        when(planStore.findById(PLAN_ID)).thenReturn(Optional.of(plan(true, 5)));

        assertThatThrownBy(() -> service.deactivate(PLAN_ID, 4, ACTOR))
                .isInstanceOf(PlanVersionConflictException.class)
                .hasMessageContaining("modified by another operation");

        verify(planStore, never())
                .changeActive(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyBoolean(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());

        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    private static PlanDetails plan(boolean active, long version) {
        return new PlanDetails(
                PLAN_ID,
                "PLAN-000001",
                "Monthly Access",
                "Unlimited gym access.",
                1,
                DurationUnit.MONTH,
                new BigDecimal("25.00"),
                "USD",
                active,
                NOW.minusSeconds(3_600),
                NOW,
                version);
    }
}
