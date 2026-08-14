package io.github.guillermodubon.coachgym.plan.application;

import io.github.guillermodubon.coachgym.plan.PlanDetails;
import io.github.guillermodubon.coachgym.plan.domain.PlanDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PlanStore {

    PlanDetails create(PlanDefinition definition, AuthenticatedActor actor, Instant occurredAt);

    PlanPage findAll(PlanSearchQuery query);

    Optional<PlanDetails> findById(UUID id);

    PlanDetails update(
            UUID id,
            PlanDefinition definition,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt);

    PlanDetails changeActive(
            UUID id,
            boolean active,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt);
}
