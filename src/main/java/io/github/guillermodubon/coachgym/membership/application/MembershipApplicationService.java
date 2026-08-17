package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.client.ClientDetails;
import io.github.guillermodubon.coachgym.client.ClientQuery;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.membership.MembershipCreated;
import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.domain.MembershipCreation;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPeriodDates;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPeriodPolicy;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPricingSnapshot;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPromotionSnapshot;
import io.github.guillermodubon.coachgym.membership.domain.MembershipValidationException;
import io.github.guillermodubon.coachgym.plan.PlanDetails;
import io.github.guillermodubon.coachgym.plan.PlanQuery;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationRequest;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationResult;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluator;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipApplicationService {

    private final MembershipStore membershipStore;
    private final ClientQuery clientQuery;
    private final PlanQuery planQuery;
    private final PromotionEvaluator promotionEvaluator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public MembershipApplicationService(
            MembershipStore membershipStore,
            ClientQuery clientQuery,
            PlanQuery planQuery,
            PromotionEvaluator promotionEvaluator,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {

        this.membershipStore = membershipStore;
        this.clientQuery = clientQuery;
        this.planQuery = planQuery;
        this.promotionEvaluator = promotionEvaluator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public MembershipDetails create(
            CreateMembershipCommand command,
            AuthenticatedActor actor) {

        validateCommand(command);
        validateActor(actor);

        ClientDetails client =
                requireActiveClient(
                        command.clientId());

        PlanDetails plan =
                requireActivePlan(
                        command.membershipPlanId());

        ensureNoCurrentMembership(
                client.id());

        MembershipPricingSnapshot pricing =
                createPricingSnapshot(
                        command,
                        plan);

        MembershipPeriodDates dates =
                MembershipPeriodPolicy.calculate(
                        command.startsOn(),
                        plan.durationValue(),
                        plan.durationUnit());

        MembershipCreation creation =
                new MembershipCreation(
                        client.id(),
                        dates,
                        pricing);

        Instant occurredAt =
                clock.instant();

        MembershipDetails membership =
                membershipStore.create(
                        creation,
                        actor,
                        occurredAt);

        publishCreated(
                membership,
                actor,
                occurredAt);

        return membership;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public MembershipDetails findById(
            UUID membershipId) {

        if (membershipId == null) {
            throw new MembershipValidationException(
                    "Membership identifier must be provided.");
        }

        return membershipStore
                .findById(membershipId)
                .orElseThrow(
                        () ->
                                new MembershipNotFoundException(
                                        membershipId));
    }

    private ClientDetails requireActiveClient(
            UUID clientId) {

        ClientDetails client =
                clientQuery.findClientById(clientId)
                        .orElseThrow(
                                () ->
                                        new MembershipClientNotFoundException(
                                                clientId));

        if (client.status()
                != ClientStatus.ACTIVE) {

            throw new InactiveMembershipClientException(
                    clientId);
        }

        return client;
    }

    private PlanDetails requireActivePlan(
            UUID membershipPlanId) {

        return planQuery.findActiveById(
                        membershipPlanId)
                .orElseThrow(
                        () ->
                                new MembershipPlanNotAvailableException(
                                        membershipPlanId));
    }

    private void ensureNoCurrentMembership(
            UUID clientId) {

        if (membershipStore.existsCurrentByClientId(
                clientId)) {

            throw new CurrentMembershipAlreadyExistsException(
                    clientId);
        }
    }

    private MembershipPricingSnapshot createPricingSnapshot(
            CreateMembershipCommand command,
            PlanDetails plan) {

        if (command.promotionId() == null) {
            return MembershipPricingSnapshot.withoutPromotion(
                    plan.id(),
                    plan.planCode(),
                    plan.name(),
                    plan.durationValue(),
                    plan.durationUnit(),
                    plan.listPrice(),
                    plan.currency());
        }

        PromotionEvaluationResult evaluation =
                promotionEvaluator.evaluate(
                        new PromotionEvaluationRequest(
                                command.promotionId(),
                                plan.id(),
                                plan.listPrice(),
                                plan.currency(),
                                command.startsOn()));

        MembershipPromotionSnapshot promotion =
                new MembershipPromotionSnapshot(
                        evaluation.promotionId(),
                        evaluation.promotionCode(),
                        evaluation.promotionName(),
                        evaluation.discountType(),
                        evaluation.discountValue(),
                        evaluation.promotionCurrency());

        return new MembershipPricingSnapshot(
                plan.id(),
                plan.planCode(),
                plan.name(),
                plan.durationValue(),
                plan.durationUnit(),
                evaluation.listPrice(),
                evaluation.currency(),
                promotion,
                evaluation.discountAmount(),
                evaluation.finalPrice());
    }

    private void publishCreated(
            MembershipDetails membership,
            AuthenticatedActor actor,
            Instant occurredAt) {

        var period =
                membership.currentPeriod();

        var pricing =
                period.pricing();

        UUID promotionId =
                pricing.promotion() == null
                        ? null
                        : pricing.promotion()
                          .promotionId();

        eventPublisher.publishEvent(
                new MembershipCreated(
                        membership.id(),
                        membership.membershipCode(),
                        membership.clientId(),
                        period.id(),
                        pricing.membershipPlanId(),
                        promotionId,
                        pricing.listPrice(),
                        pricing.discountAmount(),
                        pricing.finalPrice(),
                        pricing.currency(),
                        period.startsOn(),
                        period.effectiveEndsOn(),
                        actor.id(),
                        actor.username(),
                        occurredAt));
    }

    private static void validateCommand(
            CreateMembershipCommand command) {

        if (command == null) {
            throw new MembershipValidationException(
                    "Create membership command must be provided.");
        }

        if (command.clientId() == null) {
            throw new MembershipValidationException(
                    "Membership client identifier must be provided.");
        }

        if (command.membershipPlanId() == null) {
            throw new MembershipValidationException(
                    "Membership plan identifier must be provided.");
        }

        if (command.startsOn() == null) {
            throw new MembershipValidationException(
                    "Membership start date must be provided.");
        }
    }

    private static void validateActor(
            AuthenticatedActor actor) {

        if (actor == null) {
            throw new MembershipValidationException(
                    "Membership actor must be provided.");
        }

        if (actor.id() == null) {
            throw new MembershipValidationException(
                    "Membership actor identifier must be provided.");
        }

        if (actor.username() == null
                || actor.username().isBlank()) {

            throw new MembershipValidationException(
                    "Membership actor username must be provided.");
        }
    }
}
