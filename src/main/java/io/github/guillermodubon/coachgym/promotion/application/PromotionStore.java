package io.github.guillermodubon.coachgym.promotion.application;

import io.github.guillermodubon.coachgym.promotion.PromotionDetails;
import io.github.guillermodubon.coachgym.promotion.domain.PromotionDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PromotionStore {

    PromotionDetails create(
            PromotionDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt);

    PromotionPage findAll(
            PromotionSearchQuery query);

    Optional<PromotionDetails> findById(
            UUID id);

    PromotionDetails update(
            UUID id,
            PromotionDefinition definition,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt);

    PromotionDetails changeActive(
            UUID id,
            boolean active,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt);
}
