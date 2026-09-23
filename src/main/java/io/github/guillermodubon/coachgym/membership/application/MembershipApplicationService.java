package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.client.ClientDetails;
import io.github.guillermodubon.coachgym.client.ClientQuery;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.membership.MembershipCreated;
import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.MembershipRenewed;
import io.github.guillermodubon.coachgym.membership.domain.*;
import io.github.guillermodubon.coachgym.plan.PlanDetails;
import io.github.guillermodubon.coachgym.plan.PlanQuery;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationRequest;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationResult;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluator;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.BranchOperationContext;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.BranchOwnedResourceReference;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final BranchOperationContextResolver branchContextResolver;

    @Autowired
    public MembershipApplicationService(
            MembershipStore membershipStore,
            ClientQuery clientQuery,
            PlanQuery planQuery,
            PromotionEvaluator promotionEvaluator,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            BranchOperationContextResolver branchContextResolver) {

        this.membershipStore = membershipStore;
        this.clientQuery = clientQuery;
        this.planQuery = planQuery;
        this.promotionEvaluator = promotionEvaluator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.branchContextResolver = branchContextResolver;
    }

    public MembershipApplicationService(
            MembershipStore membershipStore,
            ClientQuery clientQuery,
            PlanQuery planQuery,
            PromotionEvaluator promotionEvaluator,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this(membershipStore, clientQuery, planQuery, promotionEvaluator,
                eventPublisher, clock, null);
    }

    @Transactional
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public MembershipDetails create(
            CreateMembershipCommand command,
            AuthenticatedActor actor) {

        validateCommand(command);
        validateActor(actor);

        UUID branchId = branchForCreation(actor);

        ClientDetails client =
                requireActiveClient(
                        command.clientId(),
                        branchId);

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

        MembershipDetails membership = branchContextResolver == null
                ? membershipStore.create(creation, actor, occurredAt)
                : membershipStore.create(creation, actor, occurredAt, branchId);

        publishCreated(
                membership,
                actor,
                occurredAt);

        return membership;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public MembershipDetails renew(
            UUID membershipId,
            RenewMembershipCommand command,
            AuthenticatedActor actor) {

        validateRenewalRequest(
                membershipId,
                command,
                actor);

        MembershipDetails currentMembership =
                requireMembership(
                        membershipId,
                        actor);

        verifyVersion(
                membershipId,
                command.version(),
                currentMembership.version());

        ClientDetails client =
                requireActiveClient(
                        currentMembership.clientId(),
                        currentMembership.registeredAtBranchId());

        PlanDetails plan =
                requireActivePlan(
                        command.membershipPlanId());

        LocalDate today =
                LocalDate.now(clock);

        MembershipRenewalPolicy.RenewalDecision decision =
                MembershipRenewalPolicy.evaluate(
                        currentMembership.id(),
                        currentMembership.status(),
                        currentMembership.currentPeriod(),
                        command.startsOn(),
                        today,
                        plan.durationValue(),
                        plan.durationUnit());

        MembershipPricingSnapshot pricing =
                createPricingSnapshot(
                        plan,
                        command.promotionId(),
                        decision.dates().startsOn());

        MembershipRenewal renewal =
                new MembershipRenewal(
                        decision.periodNumber(),
                        decision.previousStatus(),
                        decision.resultingStatus(),
                        decision.dates(),
                        pricing);

        Instant occurredAt =
                clock.instant();

        MembershipDetails renewedMembership =
                membershipStore.renew(
                        membershipId,
                        renewal,
                        command.version(),
                        actor,
                        occurredAt);

        publishRenewed(
                renewedMembership,
                renewal,
                actor,
                occurredAt);

        return renewedMembership;
    }


    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public MembershipDetails findById(
            UUID membershipId) {

        if (membershipId == null) {
            throw new MembershipValidationException(
                    "Membership identifier must be provided.");
        }

        return requireMembership(
                membershipId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public MembershipDetails findById(
            UUID membershipId,
            AuthenticatedActor actor) {
        if (membershipId == null) {
            throw new MembershipValidationException(
                    "Membership identifier must be provided.");
        }
        return requireMembership(membershipId, actor);
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

    private ClientDetails requireActiveClient(
            UUID clientId,
            UUID homeBranchId) {
        if (branchContextResolver == null || homeBranchId == null) {
            return requireActiveClient(clientId);
        }

        ClientDetails client =
                clientQuery.findClientById(clientId, homeBranchId)
                        .orElseThrow(
                                () -> new MembershipClientNotFoundException(clientId));

        if (client.status() != ClientStatus.ACTIVE) {
            throw new InactiveMembershipClientException(clientId);
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

        return createPricingSnapshot(
                plan,
                command.promotionId(),
                command.startsOn());
    }

    private MembershipPricingSnapshot createPricingSnapshot(
            PlanDetails plan,
            UUID promotionId,
            LocalDate applicableOn) {

        if (promotionId == null) {
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
                                promotionId,
                                plan.id(),
                                plan.listPrice(),
                                plan.currency(),
                                applicableOn));

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
                        occurredAt,
                        membership.registeredAtBranchId()));
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

    private MembershipDetails requireMembership(
            UUID membershipId) {

        return membershipStore.findById(
                        membershipId)
                .orElseThrow(
                        () -> new MembershipNotFoundException(
                                membershipId));
    }

    private MembershipDetails requireMembership(
            UUID membershipId,
            AuthenticatedActor actor) {
        MembershipDetails membership = requireMembership(membershipId);
        authorizeMembershipBranch(membership, actor);
        UUID branchId = membership.registeredAtBranchId();
        return branchId == null || branchContextResolver == null
                ? membership
                : membershipStore.findById(membershipId, branchId)
                        .orElseThrow(() -> new MembershipNotFoundException(membershipId));
    }

    private UUID branchForCreation(AuthenticatedActor actor) {
        if (branchContextResolver == null) {
            return null;
        }
        BranchOperationContext context = branchContextResolver.resolveOperation(actor.id());
        return BranchResourceAuthorizationPolicy.requireCreationBranch(context, null);
    }

    private void authorizeMembershipBranch(
            MembershipDetails membership,
            AuthenticatedActor actor) {
        if (branchContextResolver == null || membership.registeredAtBranchId() == null) {
            return;
        }
        BranchOperationContext context = branchContextResolver.resolveOperation(actor.id());
        BranchResourceAuthorizationPolicy.requireActiveResourceAccess(
                context,
                new BranchOwnedResourceReference(
                        membership.id(), context.organizationId(), membership.registeredAtBranchId()));
    }

    private static void verifyVersion(
            UUID membershipId,
            long expectedVersion,
            long currentVersion) {

        if (expectedVersion != currentVersion) {
            throw new MembershipVersionConflictException(
                    membershipId,
                    expectedVersion,
                    currentVersion);
        }
    }

    private static void validateRenewalRequest(
            UUID membershipId,
            RenewMembershipCommand command,
            AuthenticatedActor actor) {

        if (membershipId == null) {
            throw new MembershipValidationException(
                    "Membership identifier must be provided.");
        }

        if (command == null) {
            throw new MembershipValidationException(
                    "Renew membership command must be provided.");
        }

        validateActor(actor);
    }

    private void publishRenewed(
            MembershipDetails membership,
            MembershipRenewal renewal,
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
                new MembershipRenewed(
                        membership.id(),
                        membership.membershipCode(),
                        membership.clientId(),
                        period.id(),
                        period.periodNumber(),
                        pricing.membershipPlanId(),
                        promotionId,
                        pricing.listPrice(),
                        pricing.discountAmount(),
                        pricing.finalPrice(),
                        pricing.currency(),
                        period.startsOn(),
                        period.effectiveEndsOn(),
                        renewal.previousStatus(),
                        renewal.resultingStatus(),
                        actor.id(),
                        actor.username(),
                        occurredAt,
                        membership.registeredAtBranchId()));
    }

}
