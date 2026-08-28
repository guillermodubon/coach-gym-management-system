package io.github.guillermodubon.coachgym.payment.domain;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cross-object business rules for payment registration.
 *
 * <p>Receives flat values resolved by the application layer from
 * {@code MembershipPaymentDetails} and the inbound command.
 * Produces a validated {@link PaymentRegistration} or throws
 * a typed exception.</p>
 *
 * <p>Membership state policy:
 * <ul>
 *   <li>ACTIVE   — allowed</li>
 *   <li>FROZEN   — allowed</li>
 *   <li>EXPIRED  — allowed when the period belongs to the membership</li>
 *   <li>CANCELLED — rejected</li>
 * </ul>
 * The period-belongs-to-membership check is performed by the application
 * layer before calling this policy.</p>
 */
public final class PaymentRegistrationPolicy {

    private PaymentRegistrationPolicy() {
    }

    /**
     * Validates cross-object rules and produces a {@link PaymentRegistration}.
     *
     * @param clientId             client from the command
     * @param membershipId         membership from the command
     * @param membershipPeriodId   period from the command
     * @param amount               amount from the command (pre-normalized by caller)
     * @param currency             currency from the command (pre-normalized by caller)
     * @param paymentMethod        method from the command
     * @param externalReference    optional reference from the command (pre-normalized)
     * @param paidAt               declared payment instant from the command
     * @param membershipClientId   clientId stored on the membership record
     * @param membershipStatus     current status of the membership
     * @param periodFinalPrice     finalPrice from the membership period pricing snapshot
     * @param periodCurrency       currency from the membership period pricing snapshot
     * @param now                  current clock instant for future-paidAt guard
     */
    public static PaymentRegistration validate(
            UUID clientId,
            UUID membershipId,
            UUID membershipPeriodId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod,
            String externalReference,
            Instant paidAt,
            UUID membershipClientId,
            MembershipStatus membershipStatus,
            BigDecimal periodFinalPrice,
            String periodCurrency,
            Instant now) {

        validateRequiredInputs(
                membershipClientId,
                membershipStatus,
                periodFinalPrice,
                periodCurrency,
                now);

        // Build PaymentRegistration first — enforces field-level invariants.
        PaymentRegistration registration =
                new PaymentRegistration(
                        clientId,
                        membershipId,
                        membershipPeriodId,
                        amount,
                        currency,
                        paymentMethod,
                        externalReference,
                        paidAt);

        validateClientMembershipOwnership(
                registration.clientId(),
                membershipClientId,
                membershipId);

        validateMembershipStatus(
                membershipId,
                membershipStatus);

        validateAmount(
                registration.amount(),
                periodFinalPrice,
                membershipId,
                membershipPeriodId);

        validateCurrency(
                registration.currency(),
                periodCurrency,
                membershipId,
                membershipPeriodId);

        validatePaidAt(
                registration.paidAt(),
                now);

        return registration;
    }

    // ---------------------------------------------------------------
    // Private guard methods — each throws a distinct exception type
    // so the application layer can map them to distinct error codes.
    // ---------------------------------------------------------------

    private static void validateRequiredInputs(
            UUID membershipClientId,
            MembershipStatus membershipStatus,
            BigDecimal periodFinalPrice,
            String periodCurrency,
            Instant now) {

        if (membershipClientId == null) {
            throw new PaymentValidationException(
                    "Membership client identifier must be provided for policy validation.");
        }

        if (membershipStatus == null) {
            throw new PaymentValidationException(
                    "Membership status must be provided for policy validation.");
        }

        if (periodFinalPrice == null) {
            throw new PaymentValidationException(
                    "Membership period final price must be provided for policy validation.");
        }

        if (periodCurrency == null || periodCurrency.isBlank()) {
            throw new PaymentValidationException(
                    "Membership period currency must be provided for policy validation.");
        }

        if (now == null) {
            throw new PaymentValidationException(
                    "Current instant must be provided for policy validation.");
        }
    }

    private static void validateClientMembershipOwnership(
            UUID clientId,
            UUID membershipClientId,
            UUID membershipId) {

        if (!clientId.equals(membershipClientId)) {
            throw new PaymentMembershipMismatchException(
                    clientId,
                    membershipId);
        }
    }

    private static void validateMembershipStatus(
            UUID membershipId,
            MembershipStatus status) {

        if (status == MembershipStatus.CANCELLED) {
            throw new PaymentMembershipStateConflictException(
                    membershipId,
                    status);
        }
    }

    private static void validateAmount(
            BigDecimal amount,
            BigDecimal periodFinalPrice,
            UUID membershipId,
            UUID membershipPeriodId) {

        if (amount.compareTo(periodFinalPrice) != 0) {
            throw new PaymentAmountMismatchException(
                    membershipId,
                    membershipPeriodId,
                    amount,
                    periodFinalPrice);
        }
    }

    private static void validateCurrency(
            String currency,
            String periodCurrency,
            UUID membershipId,
            UUID membershipPeriodId) {

        if (!currency.equals(periodCurrency)) {
            throw new PaymentCurrencyMismatchException(
                    membershipId,
                    membershipPeriodId,
                    currency,
                    periodCurrency);
        }
    }

    private static void validatePaidAt(
            Instant paidAt,
            Instant now) {

        if (paidAt.isAfter(now)) {
            throw new PaymentValidationException(
                    "Payment paid-at timestamp must not be in the future.");
        }
    }
}
