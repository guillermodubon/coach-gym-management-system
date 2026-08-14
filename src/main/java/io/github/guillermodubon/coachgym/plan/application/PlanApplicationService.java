package io.github.guillermodubon.coachgym.plan.application;

import io.github.guillermodubon.coachgym.plan.PlanChangeType;
import io.github.guillermodubon.coachgym.plan.PlanChanged;
import io.github.guillermodubon.coachgym.plan.PlanDetails;
import io.github.guillermodubon.coachgym.plan.PlanQuery;
import io.github.guillermodubon.coachgym.plan.domain.PlanDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanApplicationService implements PlanQuery {

    private final PlanStore planStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PlanApplicationService(
            PlanStore planStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.planStore = planStore;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PlanDetails create(CreatePlanCommand command, AuthenticatedActor actor) {
        PlanDefinition definition = toDefinition(command);
        Instant occurredAt = clock.instant();
        PlanDetails plan = planStore.create(definition, actor, occurredAt);
        publish(plan, PlanChangeType.CREATED, actor, occurredAt);
        return plan;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PlanPage findAll(PlanSearchQuery query) {
        return planStore.findAll(query);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PlanDetails findById(UUID id) {
        return planStore.findById(id).orElseThrow(() -> new PlanNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlanDetails> findActiveById(UUID id) {
        return planStore.findById(id).filter(PlanDetails::active);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PlanDetails update(UUID id, UpdatePlanCommand command, AuthenticatedActor actor) {
        PlanDefinition definition = toDefinition(command);
        Instant occurredAt = clock.instant();
        PlanDetails plan = planStore.update(
                id,
                definition,
                command.version(),
                actor,
                occurredAt);
        publish(plan, PlanChangeType.UPDATED, actor, occurredAt);
        return plan;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PlanDetails deactivate(UUID id, long expectedVersion, AuthenticatedActor actor) {
        return changeActive(id, false, expectedVersion, actor, PlanChangeType.DEACTIVATED);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PlanDetails activate(UUID id, long expectedVersion, AuthenticatedActor actor) {
        return changeActive(id, true, expectedVersion, actor, PlanChangeType.REACTIVATED);
    }

    private PlanDetails changeActive(
            UUID id,
            boolean active,
            long expectedVersion,
            AuthenticatedActor actor,
            PlanChangeType changeType) {
        Instant occurredAt = clock.instant();
        PlanDetails current = planStore.findById(id).orElseThrow(() -> new PlanNotFoundException(id));
        if (current.version() != expectedVersion) {
            throw new PlanVersionConflictException();
        }
        if (current.active() == active) {
            String state = active ? "active" : "inactive";
            throw new PlanStateConflictException("Plan is already " + state + ".");
        }
        PlanDetails plan = planStore.changeActive(id, active, expectedVersion, actor, occurredAt);
        publish(plan, changeType, actor, occurredAt);
        return plan;
    }

    private void publish(
            PlanDetails plan,
            PlanChangeType changeType,
            AuthenticatedActor actor,
            Instant occurredAt) {
        eventPublisher.publishEvent(new PlanChanged(
                plan.id(),
                plan.planCode(),
                changeType,
                actor.id(),
                actor.username(),
                occurredAt));
    }

    private static PlanDefinition toDefinition(CreatePlanCommand command) {
        return PlanDefinition.create(
                command.name(),
                command.description(),
                command.durationValue(),
                command.durationUnit(),
                command.listPrice(),
                command.currency());
    }

    private static PlanDefinition toDefinition(UpdatePlanCommand command) {
        return PlanDefinition.create(
                command.name(),
                command.description(),
                command.durationValue(),
                command.durationUnit(),
                command.listPrice(),
                command.currency());
    }
}
