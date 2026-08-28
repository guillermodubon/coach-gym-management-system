package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.membership.MembershipPaymentDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPaymentPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPaymentQuery;
import io.github.guillermodubon.coachgym.payment.PaymentDetails;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentRegistered;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.domain.PaymentAmountMismatchException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentCurrencyMismatchException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentMembershipMismatchException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentMembershipStateConflictException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentRegistration;
import io.github.guillermodubon.coachgym.payment.domain.PaymentRegistrationPolicy;
import io.github.guillermodubon.coachgym.payment.domain.PaymentValidationException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentApplicationService {

    private final PaymentStore paymentStore;
    private final MembershipPaymentQuery membershipPaymentQuery;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PaymentApplicationService(
            PaymentStore paymentStore,
            MembershipPaymentQuery membershipPaymentQuery,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {

        this.paymentStore = paymentStore;
        this.membershipPaymentQuery = membershipPaymentQuery;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PaymentDetails register(
            RegisterPaymentCommand command,
            AuthenticatedActor actor) {

        validateCommand(command);
        validateActor(actor);

        MembershipPaymentDetails membership =
                membershipPaymentQuery
                        .findMembershipForPayment(command.membershipId())
                        .orElseThrow(() ->
                                new PaymentMembershipNotFoundException(
                                        command.membershipId()));

        MembershipPaymentPeriodDetails period =
                membershipPaymentQuery
                        .findPeriodForPayment(command.membershipPeriodId())
                        .orElseThrow(() ->
                                new PaymentPeriodNotFoundException(
                                        command.membershipPeriodId()));

        if (!period.membershipId().equals(command.membershipId())) {
            throw new PaymentPeriodMismatchException(
                    command.membershipId(),
                    command.membershipPeriodId());
        }

        Instant now = clock.instant();

        // Domain policy validates ownership, state, amount, currency, paidAt.
        // Throws typed domain exceptions that the controller maps to error codes.
        PaymentRegistration registration =
                PaymentRegistrationPolicy.validate(
                        command.clientId(),
                        command.membershipId(),
                        command.membershipPeriodId(),
                        command.amount(),
                        command.currency(),
                        command.paymentMethod(),
                        command.externalReference(),
                        command.paidAt(),
                        membership.clientId(),
                        membership.status(),
                        period.finalPrice(),
                        period.currency(),
                        now);

        if (registration.externalReference() != null
                && paymentStore.existsByMethodAndExternalReference(
                        registration.paymentMethod(),
                        registration.externalReference())) {

            throw new DuplicatePaymentReferenceException(
                    registration.paymentMethod(),
                    registration.externalReference());
        }

        PaymentDetails payment =
                paymentStore.register(
                        registration.clientId(),
                        registration.membershipId(),
                        registration.membershipPeriodId(),
                        registration.amount(),
                        registration.currency(),
                        registration.paymentMethod(),
                        registration.externalReference(),
                        registration.paidAt(),
                        actor,
                        now);

        eventPublisher.publishEvent(
                new PaymentRegistered(
                        payment.id(),
                        payment.paymentCode(),
                        payment.clientId(),
                        payment.membershipId(),
                        payment.membershipPeriodId(),
                        payment.amount(),
                        payment.currency(),
                        payment.paymentMethod(),
                        payment.externalReference() != null,
                        payment.paidAt(),
                        PaymentStatus.PAID,
                        actor.id(),
                        actor.username(),
                        now));

        return payment;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PaymentPage findAll(PaymentSearchQuery query) {

        if (query == null) {
            throw new PaymentValidationException(
                    "Payment search query must be provided.");
        }

        return paymentStore.findAll(query);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PaymentDetails findById(UUID paymentId) {

        if (paymentId == null) {
            throw new PaymentValidationException(
                    "Payment identifier must be provided.");
        }

        return paymentStore.findById(paymentId)
                .orElseThrow(() ->
                        new PaymentNotFoundException(paymentId));
    }

    // ------------------------------------------------------------------
    // Private guards
    // ------------------------------------------------------------------

    private static void validateCommand(
            RegisterPaymentCommand command) {

        if (command == null) {
            throw new PaymentValidationException(
                    "Register payment command must be provided.");
        }

        if (command.clientId() == null) {
            throw new PaymentValidationException(
                    "Payment client identifier must be provided.");
        }

        if (command.membershipId() == null) {
            throw new PaymentValidationException(
                    "Payment membership identifier must be provided.");
        }

        if (command.membershipPeriodId() == null) {
            throw new PaymentValidationException(
                    "Payment membership period identifier must be provided.");
        }

        if (command.amount() == null) {
            throw new PaymentValidationException(
                    "Payment amount must be provided.");
        }

        if (command.currency() == null
                || command.currency().isBlank()) {

            throw new PaymentValidationException(
                    "Payment currency must be provided.");
        }

        if (command.paymentMethod() == null) {
            throw new PaymentValidationException(
                    "Payment method must be provided.");
        }

        if (command.paidAt() == null) {
            throw new PaymentValidationException(
                    "Payment paid-at timestamp must be provided.");
        }
    }

    private static void validateActor(
            AuthenticatedActor actor) {

        if (actor == null) {
            throw new PaymentValidationException(
                    "Payment actor must be provided.");
        }

        if (actor.id() == null) {
            throw new PaymentValidationException(
                    "Payment actor identifier must be provided.");
        }

        if (actor.username() == null
                || actor.username().isBlank()) {

            throw new PaymentValidationException(
                    "Payment actor username must be provided.");
        }
    }
}
