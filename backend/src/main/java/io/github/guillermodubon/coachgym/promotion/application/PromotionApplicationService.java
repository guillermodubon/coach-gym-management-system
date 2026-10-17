package io.github.guillermodubon.coachgym.promotion.application;

import io.github.guillermodubon.coachgym.promotion.PromotionChanged;
import io.github.guillermodubon.coachgym.promotion.PromotionChangeType;
import io.github.guillermodubon.coachgym.promotion.PromotionDetails;
import io.github.guillermodubon.coachgym.promotion.domain.PromotionDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionApplicationService {

    private final PromotionStore promotionStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PromotionApplicationService(
            PromotionStore promotionStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {

        this.promotionStore = promotionStore;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PromotionDetails create(
            CreatePromotionCommand command,
            AuthenticatedActor actor) {

        PromotionDefinition definition = toDefinition(command);
        Instant occurredAt = clock.instant();

        PromotionDetails promotion =
                promotionStore.create(
                        definition,
                        actor,
                        occurredAt);

        publishCreated(promotion, actor, occurredAt);

        return promotion;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PromotionPage findAll(
            PromotionSearchQuery query) {

        return promotionStore.findAll(query);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PromotionDetails update(
            UUID id,
            UpdatePromotionCommand command,
            AuthenticatedActor actor) {

        PromotionDefinition definition =
                toDefinition(command);

        Instant occurredAt = clock.instant();

        PromotionDetails promotion =
                promotionStore.update(
                        id,
                        definition,
                        command.version(),
                        actor,
                        occurredAt);

        publishChanged(
                promotion,
                PromotionChangeType.UPDATED,
                actor,
                occurredAt);

        return promotion;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PromotionDetails deactivate(
            UUID id,
            long expectedVersion,
            AuthenticatedActor actor) {

        return changeActive(
                id,
                false,
                expectedVersion,
                actor);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PromotionDetails activate(
            UUID id,
            long expectedVersion,
            AuthenticatedActor actor) {

        return changeActive(
                id,
                true,
                expectedVersion,
                actor);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PromotionDetails findById(UUID id) {
        return promotionStore
                .findById(id)
                .orElseThrow(() -> new PromotionNotFoundException(id));
    }

    private void publishCreated(
            PromotionDetails promotion,
            AuthenticatedActor actor,
            Instant occurredAt) {

        publishChanged(
                promotion,
                PromotionChangeType.CREATED,
                actor,
                occurredAt);
    }

    private static PromotionDefinition toDefinition(
            CreatePromotionCommand command) {

        if (command == null) {
            throw new IllegalArgumentException(
                    "Create promotion command must be provided.");
        }

        return PromotionDefinition.create(
                command.name(),
                command.description(),
                command.discountType(),
                command.discountValue(),
                command.currency(),
                command.validFrom(),
                command.validUntil());
    }

    private PromotionDetails changeActive(
            UUID id,
            boolean active,
            long expectedVersion,
            AuthenticatedActor actor) {

        if (expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "Promotion version must not be negative.");
        }

        PromotionDetails current =
                promotionStore.findById(id)
                        .orElseThrow(
                                () -> new PromotionNotFoundException(id));

        verifyVersion(
                id,
                expectedVersion,
                current.version());

        if (current.active() == active) {
            throw new PromotionStateConflictException(
                    current.active());
        }

        Instant occurredAt = clock.instant();

        PromotionDetails promotion =
                promotionStore.changeActive(
                        id,
                        active,
                        expectedVersion,
                        actor,
                        occurredAt);

        publishChanged(
                promotion,
                active
                        ? PromotionChangeType.REACTIVATED
                        : PromotionChangeType.DEACTIVATED,
                actor,
                occurredAt);

        return promotion;
    }

    private static void verifyVersion(
            UUID id,
            long expectedVersion,
            long currentVersion) {

        if (expectedVersion != currentVersion) {
            throw new PromotionVersionConflictException(
                    id,
                    expectedVersion,
                    currentVersion);
        }
    }

    private static PromotionDefinition toDefinition(
            UpdatePromotionCommand command) {

        if (command == null) {
            throw new IllegalArgumentException(
                    "Update promotion command must be provided.");
        }

        if (command.version() < 0) {
            throw new IllegalArgumentException(
                    "Promotion version must not be negative.");
        }

        return PromotionDefinition.create(
                command.name(),
                command.description(),
                command.discountType(),
                command.discountValue(),
                command.currency(),
                command.validFrom(),
                command.validUntil());
    }

    private void publishChanged(
            PromotionDetails promotion,
            PromotionChangeType changeType,
            AuthenticatedActor actor,
            Instant occurredAt) {

        eventPublisher.publishEvent(
                new PromotionChanged(
                        promotion.id(),
                        promotion.promotionCode(),
                        changeType,
                        actor.id(),
                        actor.username(),
                        occurredAt));
    }
}
