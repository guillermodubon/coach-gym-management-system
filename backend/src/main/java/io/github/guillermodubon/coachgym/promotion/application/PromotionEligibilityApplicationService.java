package io.github.guillermodubon.coachgym.promotion.application;

import io.github.guillermodubon.coachgym.plan.PlanCatalogQuery;
import io.github.guillermodubon.coachgym.plan.PlanDetails;
import io.github.guillermodubon.coachgym.promotion.PromotionDetails;
import io.github.guillermodubon.coachgym.promotion.PromotionEligiblePlan;
import io.github.guillermodubon.coachgym.promotion.PromotionPlanEligibilityChanged;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionEligibilityApplicationService {

    private final PromotionStore promotionStore;

    private final PromotionEligibilityStore
            eligibilityStore;

    private final PlanCatalogQuery planCatalogQuery;

    private final ApplicationEventPublisher
            eventPublisher;

    private final Clock clock;

    public PromotionEligibilityApplicationService(
            PromotionStore promotionStore,
            PromotionEligibilityStore eligibilityStore,
            PlanCatalogQuery planCatalogQuery,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {

        this.promotionStore =
                promotionStore;

        this.eligibilityStore =
                eligibilityStore;

        this.planCatalogQuery =
                planCatalogQuery;

        this.eventPublisher =
                eventPublisher;

        this.clock =
                clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PromotionEligibilityDetails findEligiblePlans(
            UUID promotionId) {

        PromotionDetails promotion =
                requirePromotion(promotionId);

        Set<UUID> eligiblePlanIds =
                eligibilityStore.findEligiblePlanIds(
                        promotionId);

        List<PromotionEligiblePlan> eligiblePlans =
                toEligiblePlans(
                        eligiblePlanIds,
                        false);

        return new PromotionEligibilityDetails(
                promotion.id(),
                promotion.version(),
                eligiblePlans);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PromotionEligibilityDetails replaceEligiblePlans(
            UUID promotionId,
            ReplaceEligiblePlansCommand command,
            AuthenticatedActor actor) {

        if (command == null) {
            throw new IllegalArgumentException(
                    "Replace eligible plans command "
                            + "must be provided.");
        }

        PromotionDetails promotion =
                requirePromotion(promotionId);

        verifyVersion(
                promotionId,
                command.promotionVersion(),
                promotion.version());

        List<PromotionEligiblePlan> requestedPlans =
                toEligiblePlans(
                        command.planIds(),
                        true);

        Instant occurredAt =
                clock.instant();

        eligibilityStore.replaceEligiblePlanIds(
                promotionId,
                command.planIds(),
                command.promotionVersion(),
                actor,
                occurredAt);

        eventPublisher.publishEvent(
                new PromotionPlanEligibilityChanged(
                        promotion.id(),
                        promotion.promotionCode(),
                        command.planIds(),
                        actor.id(),
                        actor.username(),
                        occurredAt));

        return new PromotionEligibilityDetails(
                promotion.id(),
                command.promotionVersion() + 1,
                requestedPlans);
    }

    private List<PromotionEligiblePlan> toEligiblePlans(
            Set<UUID> planIds,
            boolean requireActive) {

        if (planIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, PlanDetails> plansById =
                planCatalogQuery.findByIds(planIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        PlanDetails::id,
                                        Function.identity()));

        for (UUID planId : planIds) {
            PlanDetails plan =
                    plansById.get(planId);

            if (plan == null) {
                throw new EligiblePlanNotFoundException(
                        planId);
            }

            if (requireActive
                    && !plan.active()) {

                throw new InactiveEligiblePlanException(
                        planId);
            }
        }

        return plansById.values()
                .stream()
                .map(
                        plan ->
                                new PromotionEligiblePlan(
                                        plan.id(),
                                        plan.planCode(),
                                        plan.name(),
                                        plan.active()))
                .sorted(
                        Comparator.comparing(
                                        PromotionEligiblePlan::planName,
                                        String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(
                                        PromotionEligiblePlan::planId))
                .toList();
    }

    private PromotionDetails requirePromotion(
            UUID promotionId) {

        return promotionStore
                .findById(promotionId)
                .orElseThrow(
                        () ->
                                new PromotionNotFoundException(
                                        promotionId));
    }

    private static void verifyVersion(
            UUID promotionId,
            long expectedVersion,
            long currentVersion) {

        if (expectedVersion
                != currentVersion) {

            throw new PromotionVersionConflictException(
                    promotionId,
                    expectedVersion,
                    currentVersion);
        }
    }
}