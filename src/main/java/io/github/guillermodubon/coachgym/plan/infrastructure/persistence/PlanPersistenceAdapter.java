package io.github.guillermodubon.coachgym.plan.infrastructure.persistence;

import io.github.guillermodubon.coachgym.plan.PlanDetails;
import io.github.guillermodubon.coachgym.plan.application.PlanNotFoundException;
import io.github.guillermodubon.coachgym.plan.application.PlanPage;
import io.github.guillermodubon.coachgym.plan.application.PlanSearchQuery;
import io.github.guillermodubon.coachgym.plan.application.PlanSortDirection;
import io.github.guillermodubon.coachgym.plan.application.PlanStateConflictException;
import io.github.guillermodubon.coachgym.plan.application.PlanStore;
import io.github.guillermodubon.coachgym.plan.application.PlanVersionConflictException;
import io.github.guillermodubon.coachgym.plan.domain.PlanDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PlanPersistenceAdapter implements PlanStore {

    private final PlanJpaRepository planRepository;
    private final EntityManager entityManager;

    PlanPersistenceAdapter(PlanJpaRepository planRepository, EntityManager entityManager) {
        this.planRepository = planRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public PlanDetails create(
            PlanDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt) {
        MembershipPlanJpaEntity plan = planRepository.saveAndFlush(
                MembershipPlanJpaEntity.create(definition, actor, occurredAt));
        entityManager.refresh(plan);
        return plan.toDetails();
    }

    @Override
    @Transactional(readOnly = true)
    public PlanPage findAll(PlanSearchQuery query) {
        Page<MembershipPlanJpaEntity> page = planRepository.search(
                query.active(),
                query.name(),
                PageRequest.of(query.page(), query.size(), toSort(query)));
        return new PlanPage(
                page.getContent().stream().map(MembershipPlanJpaEntity::toDetails).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlanDetails> findById(UUID id) {
        return planRepository.findById(id).map(MembershipPlanJpaEntity::toDetails);
    }

    @Override
    @Transactional
    public PlanDetails update(
            UUID id,
            PlanDefinition definition,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {
        MembershipPlanJpaEntity plan = findEntity(id);
        ensureExpectedVersion(plan, expectedVersion);
        plan.update(definition, actor, occurredAt);
        flush(plan);
        return plan.toDetails();
    }

    @Override
    @Transactional
    public PlanDetails changeActive(
            UUID id,
            boolean active,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {
        MembershipPlanJpaEntity plan = findEntity(id);
        ensureExpectedVersion(plan, expectedVersion);
        if (plan.active() == active) {
            String state = active ? "active" : "inactive";
            throw new PlanStateConflictException("Plan is already " + state + ".");
        }
        plan.changeActive(active, actor, occurredAt);
        flush(plan);
        return plan.toDetails();
    }

    private MembershipPlanJpaEntity findEntity(UUID id) {
        return planRepository.findById(id).orElseThrow(() -> new PlanNotFoundException(id));
    }

    private static void ensureExpectedVersion(MembershipPlanJpaEntity plan, long expectedVersion) {
        if (expectedVersion < 0 || plan.version() != expectedVersion) {
            throw new PlanVersionConflictException();
        }
    }

    private void flush(MembershipPlanJpaEntity plan) {
        try {
            planRepository.saveAndFlush(plan);
            entityManager.refresh(plan);
        } catch (OptimisticLockingFailureException | OptimisticLockException exception) {
            throw new PlanVersionConflictException();
        }
    }

    private static Sort toSort(PlanSearchQuery query) {
        String property = switch (query.sortField()) {
            case NAME -> "name";
            case CREATED_AT -> "createdAt";
            case UPDATED_AT -> "updatedAt";
        };
        Sort.Direction direction = query.direction() == PlanSortDirection.ASC
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }
}
