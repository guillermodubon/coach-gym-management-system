package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.membership.MembershipPaymentDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPaymentPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPaymentQuery;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptCreated;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptFailureCode;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.domain.PaymentAttemptStateConflictException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentAttemptValidationException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentMembershipMismatchException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentMembershipStateConflictException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates staff-created hosted checkouts without spanning a transaction over provider I/O. */
@Service
public class PaymentAttemptApplicationService {

    private static final PaymentProvider PROVIDER = PaymentProvider.STRIPE;

    private final PaymentAttemptStore paymentAttemptStore;
    private final MembershipPaymentQuery membershipPaymentQuery;
    private final ObjectProvider<CardCheckoutGateway> checkoutGatewayProvider;
    private final ObjectProvider<CheckoutRedirectPolicy> redirectPolicyProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final BranchOperationContextResolver branchContextResolver;

    @Autowired
    public PaymentAttemptApplicationService(
            PaymentAttemptStore paymentAttemptStore,
            MembershipPaymentQuery membershipPaymentQuery,
            ObjectProvider<CardCheckoutGateway> checkoutGatewayProvider,
            ObjectProvider<CheckoutRedirectPolicy> redirectPolicyProvider,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            BranchOperationContextResolver branchContextResolver) {
        this.paymentAttemptStore = Objects.requireNonNull(paymentAttemptStore);
        this.membershipPaymentQuery = Objects.requireNonNull(membershipPaymentQuery);
        this.checkoutGatewayProvider = Objects.requireNonNull(checkoutGatewayProvider);
        this.redirectPolicyProvider = Objects.requireNonNull(redirectPolicyProvider);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
        this.branchContextResolver = branchContextResolver;
    }

    public PaymentAttemptApplicationService(
            PaymentAttemptStore paymentAttemptStore,
            MembershipPaymentQuery membershipPaymentQuery,
            ObjectProvider<CardCheckoutGateway> checkoutGatewayProvider,
            ObjectProvider<CheckoutRedirectPolicy> redirectPolicyProvider,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this(paymentAttemptStore, membershipPaymentQuery, checkoutGatewayProvider,
                redirectPolicyProvider, eventPublisher, clock, null);
    }

    /** Creates a provider checkout after persisting an auditable CREATED attempt. */
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PaymentAttemptCheckoutDetails createCheckout(
            CreateCardCheckoutAttemptCommand command,
            AuthenticatedActor actor) {
        validateActor(actor);
        UUID branchId = branchId(actor);
        if (command == null) {
            throw new PaymentAttemptValidationException(
                    "Create payment attempt command is required.");
        }

        MembershipPaymentDetails membership = membershipPaymentQuery
                .findMembershipForPayment(command.membershipId())
                .orElseThrow(() -> new PaymentMembershipNotFoundException(command.membershipId()));
        MembershipPaymentPeriodDetails period = membershipPaymentQuery
                .findPeriodForPayment(command.membershipPeriodId())
                .orElseThrow(() -> new PaymentPeriodNotFoundException(command.membershipPeriodId()));

        if (!Objects.equals(membership.membershipId(), command.membershipId())
                || !Objects.equals(membership.clientId(), command.clientId())) {
            throw new PaymentMembershipMismatchException(command.clientId(), command.membershipId());
        }
        if (!Objects.equals(period.periodId(), command.membershipPeriodId())
                || !Objects.equals(period.membershipId(), command.membershipId())) {
            throw new PaymentPeriodMismatchException(
                    command.membershipId(), command.membershipPeriodId());
        }
        if (membership.status() == null) {
            throw new PaymentAttemptValidationException(
                    "Membership status is required for payment validation.");
        }
        if (membership.status() == MembershipStatus.CANCELLED) {
            throw new PaymentMembershipStateConflictException(
                    command.membershipId(), membership.status());
        }
        if (period.finalPrice() == null || period.finalPrice().signum() <= 0
                || period.currency() == null || period.currency().isBlank()) {
            throw new PaymentAttemptValidationException(
                    "Membership period pricing snapshot is invalid.");
        }

        Instant occurredAt = clock.instant();
        PaymentAttemptDetails created = paymentAttemptStore.create(
                new PersistPaymentAttemptCommand(
                        UUID.randomUUID(), command.clientId(), command.membershipId(),
                        command.membershipPeriodId(), PROVIDER, period.finalPrice(),
                        period.currency(), actor.id(), occurredAt, branchId));
        eventPublisher.publishEvent(new PaymentAttemptCreated(
                created.id(), created.clientId(), created.membershipId(),
                created.membershipPeriodId(), created.provider(), created.expectedAmount(),
                created.currency(), actor.id(), actor.username(), occurredAt,
                created.initiatedAtBranchId()));

        ProviderCheckout checkout;
        try {
            CardCheckoutGateway gateway = checkoutGatewayProvider.getIfAvailable();
            CheckoutRedirectPolicy redirects = redirectPolicyProvider.getIfAvailable();
            if (gateway == null || redirects == null) {
                throw new PaymentProviderException(PaymentProviderFailureCode.UNAVAILABLE);
            }
            checkout = gateway.createCheckout(new ProviderCheckoutRequest(
                    created.id(), PROVIDER, created.expectedAmount(), created.currency(),
                    redirects.successUrl(PROVIDER), redirects.cancelUrl(PROVIDER)));
            if (checkout == null || checkout.provider() != PROVIDER
                    || !checkout.expiresAt().isAfter(occurredAt)) {
                throw new PaymentProviderException(PaymentProviderFailureCode.INVALID_RESPONSE);
            }
        } catch (PaymentProviderException exception) {
            failAfterProviderError(created, actor, clock.instant(), exception);
            throw exception;
        } catch (RuntimeException exception) {
            PaymentProviderException safeFailure =
                    new PaymentProviderException(PaymentProviderFailureCode.INVALID_RESPONSE);
            failAfterProviderError(created, actor, clock.instant(), safeFailure);
            throw safeFailure;
        }

        PaymentAttemptDetails processing = paymentAttemptStore.markProcessing(
                new ProcessPaymentAttemptCommand(
                        created.id(), created.version(), checkout.checkoutReference(),
                        checkout.expiresAt(), actor.id(), clock.instant()));
        eventPublisher.publishEvent(new PaymentAttemptStatusChanged(
                processing.id(), processing.provider(), PaymentAttemptStatus.CREATED,
                processing.status(), processing.failureCode(), processing.confirmedPaymentId(),
                actor.id(), processing.updatedAt(), processing.initiatedAtBranchId()));
        return new PaymentAttemptCheckoutDetails(
                processing, checkout.checkoutUrl(), checkout.expiresAt());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PaymentAttemptDetails findById(UUID paymentAttemptId) {
        if (paymentAttemptId == null) {
            throw new PaymentAttemptValidationException("Payment attempt id is required.");
        }
        return paymentAttemptStore.findById(paymentAttemptId)
                .orElseThrow(() -> new PaymentAttemptNotFoundException(paymentAttemptId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PaymentAttemptDetails findById(
            UUID paymentAttemptId,
            AuthenticatedActor actor) {
        validateActor(actor);
        if (paymentAttemptId == null) {
            throw new PaymentAttemptValidationException("Payment attempt id is required.");
        }
        return paymentAttemptStore.findById(paymentAttemptId, branchId(actor))
                .orElseThrow(() -> new PaymentAttemptNotFoundException(paymentAttemptId));
    }

    /** Cancels a provider checkout only after the provider confirms expiration/cancellation. */
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PaymentAttemptDetails cancel(
            CancelPaymentAttemptCommand command,
            AuthenticatedActor actor) {
        validateActor(actor);
        if (command == null) {
            throw new PaymentAttemptValidationException(
                    "Cancel payment attempt command is required.");
        }

        PaymentAttemptProviderDetails providerDetails = (branchContextResolver == null
                ? paymentAttemptStore.findProviderDetails(command.paymentAttemptId())
                : paymentAttemptStore.findProviderDetails(
                        command.paymentAttemptId(), branchId(actor)))
                .orElseThrow(() -> new PaymentAttemptNotFoundException(command.paymentAttemptId()));
        PaymentAttemptDetails current = providerDetails.details();
        if (current.version() != command.expectedVersion()) {
            throw new PaymentAttemptVersionConflictException(
                    current.id(), command.expectedVersion(), current.version());
        }
        if (current.status() != PaymentAttemptStatus.PROCESSING
                || providerDetails.checkoutReference() == null) {
            throw new PaymentAttemptStateConflictException(
                    current.id(), current.status(), PaymentAttemptStatus.CANCELLED);
        }

        try {
            CardCheckoutGateway gateway = checkoutGatewayProvider.getIfAvailable();
            if (gateway == null) {
                throw new PaymentProviderException(PaymentProviderFailureCode.UNAVAILABLE);
            }
            gateway.cancelCheckout(new ProviderCheckoutCancellationRequest(
                    current.provider(), providerDetails.checkoutReference()));
        } catch (PaymentProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PaymentProviderException(PaymentProviderFailureCode.UNAVAILABLE);
        }

        Instant occurredAt = clock.instant();
        PaymentAttemptDetails cancelled = paymentAttemptStore.markCancelled(
                new CancelPaymentAttemptPersistenceCommand(
                        current.id(), current.version(), actor.id(), occurredAt));
        eventPublisher.publishEvent(new PaymentAttemptStatusChanged(
                cancelled.id(), cancelled.provider(), PaymentAttemptStatus.PROCESSING,
                cancelled.status(), cancelled.failureCode(), cancelled.confirmedPaymentId(),
                actor.id(), cancelled.updatedAt(), cancelled.initiatedAtBranchId()));
        return cancelled;
    }

    private void failAfterProviderError(
            PaymentAttemptDetails created,
            AuthenticatedActor actor,
            Instant occurredAt,
            PaymentProviderException exception) {
        PaymentAttemptFailureCode failureCode = switch (exception.failureCode()) {
            case UNAVAILABLE, TIMEOUT -> PaymentAttemptFailureCode.PROVIDER_UNAVAILABLE;
            case INVALID_RESPONSE -> PaymentAttemptFailureCode.PROVIDER_DATA_MISMATCH;
            case INVALID_SIGNATURE, UNSUPPORTED_EVENT -> PaymentAttemptFailureCode.PROVIDER_DECLINED;
        };
        PaymentAttemptDetails failed = paymentAttemptStore.markFailed(
                new FailPaymentAttemptCommand(
                        created.id(), created.version(), failureCode, actor.id(), occurredAt));
        eventPublisher.publishEvent(new PaymentAttemptStatusChanged(
                failed.id(), failed.provider(), PaymentAttemptStatus.CREATED,
                failed.status(), failed.failureCode(), failed.confirmedPaymentId(),
                actor.id(), failed.updatedAt(), failed.initiatedAtBranchId()));
    }

    private static void validateActor(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null
                || actor.username() == null || actor.username().isBlank()) {
            throw new PaymentAttemptValidationException("Authenticated actor is required.");
        }
    }

    private UUID branchId(AuthenticatedActor actor) {
        if (branchContextResolver == null) {
            return null;
        }
        return BranchResourceAuthorizationPolicy.requireActiveBranch(
                branchContextResolver.resolveOperation(actor.id()));
    }
}
