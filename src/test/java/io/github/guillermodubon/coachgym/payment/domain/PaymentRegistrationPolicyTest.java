package io.github.guillermodubon.coachgym.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentRegistrationPolicyTest {

    private static final UUID CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    private static final UUID OTHER_CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000099");

    private static final BigDecimal FINAL_PRICE =
            new BigDecimal("25.00");

    private static final String CURRENCY = "USD";

    private static final Instant NOW =
            Instant.parse("2026-08-25T18:35:00Z");

    private static final Instant PAID_AT =
            Instant.parse("2026-08-25T18:30:00Z");

    // ------------------------------------------------------------------
    // Happy-path: allowed membership states
    // ------------------------------------------------------------------

    @Test
    void allowsPaymentForActiveMembership() {
        PaymentRegistration reg = validate(MembershipStatus.ACTIVE);

        assertThat(reg.clientId()).isEqualTo(CLIENT_ID);
        assertThat(reg.amount()).isEqualByComparingTo(FINAL_PRICE);
        assertThat(reg.currency()).isEqualTo(CURRENCY);
    }

    @Test
    void allowsPaymentForFrozenMembership() {
        PaymentRegistration reg = validate(MembershipStatus.FROZEN);

        assertThat(reg.membershipId()).isEqualTo(MEMBERSHIP_ID);
    }

    @Test
    void allowsPaymentForExpiredMembership() {
        PaymentRegistration reg = validate(MembershipStatus.EXPIRED);

        assertThat(reg.membershipPeriodId()).isEqualTo(PERIOD_ID);
    }

    // ------------------------------------------------------------------
    // Membership state: CANCELLED must be rejected
    // ------------------------------------------------------------------

    @Test
    void rejectsPaymentForCancelledMembership() {
        assertThatThrownBy(
                () -> validate(MembershipStatus.CANCELLED))
                .isInstanceOf(PaymentMembershipStateConflictException.class)
                .hasMessageContaining(MEMBERSHIP_ID.toString())
                .hasMessageContaining("CANCELLED");
    }

    // ------------------------------------------------------------------
    // Client–membership ownership mismatch
    // ------------------------------------------------------------------

    @Test
    void rejectsPaymentWhenMembershipBelongsToAnotherClient() {
        assertThatThrownBy(
                () -> PaymentRegistrationPolicy.validate(
                        CLIENT_ID,
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        FINAL_PRICE,
                        CURRENCY,
                        PaymentMethod.CASH,
                        null,
                        PAID_AT,
                        OTHER_CLIENT_ID,      // membership belongs to someone else
                        MembershipStatus.ACTIVE,
                        FINAL_PRICE,
                        CURRENCY,
                        NOW))
                .isInstanceOf(PaymentMembershipMismatchException.class)
                .hasMessageContaining(CLIENT_ID.toString())
                .hasMessageContaining(MEMBERSHIP_ID.toString());
    }

    // ------------------------------------------------------------------
    // Amount mismatch
    // ------------------------------------------------------------------

    @Test
    void rejectsAmountDifferentFromFinalPrice() {
        BigDecimal wrongAmount = new BigDecimal("20.00");

        assertThatThrownBy(
                () -> PaymentRegistrationPolicy.validate(
                        CLIENT_ID,
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        wrongAmount,
                        CURRENCY,
                        PaymentMethod.CASH,
                        null,
                        PAID_AT,
                        CLIENT_ID,
                        MembershipStatus.ACTIVE,
                        FINAL_PRICE,
                        CURRENCY,
                        NOW))
                .isInstanceOf(PaymentAmountMismatchException.class)
                .hasMessageContaining("20.00")
                .hasMessageContaining("25.00")
                .hasMessageContaining(PERIOD_ID.toString());
    }

    @Test
    void acceptsExactFinalPriceFromPromotionSnapshot() {
        // Promotion scenario: finalPrice reflects discount already applied.
        BigDecimal discountedFinalPrice = new BigDecimal("22.50");

        PaymentRegistration reg = PaymentRegistrationPolicy.validate(
                CLIENT_ID,
                MEMBERSHIP_ID,
                PERIOD_ID,
                discountedFinalPrice,
                CURRENCY,
                PaymentMethod.CASH,
                null,
                PAID_AT,
                CLIENT_ID,
                MembershipStatus.ACTIVE,
                discountedFinalPrice,   // period's finalPrice includes discount
                CURRENCY,
                NOW);

        assertThat(reg.amount()).isEqualByComparingTo(discountedFinalPrice);
    }

    // ------------------------------------------------------------------
    // Currency mismatch
    // ------------------------------------------------------------------

    @Test
    void rejectsCurrencyDifferentFromPeriodCurrency() {
        assertThatThrownBy(
                () -> PaymentRegistrationPolicy.validate(
                        CLIENT_ID,
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        FINAL_PRICE,
                        "EUR",          // wrong currency
                        PaymentMethod.CASH,
                        null,
                        PAID_AT,
                        CLIENT_ID,
                        MembershipStatus.ACTIVE,
                        FINAL_PRICE,
                        CURRENCY,       // period uses USD
                        NOW))
                .isInstanceOf(PaymentCurrencyMismatchException.class)
                .hasMessageContaining("EUR")
                .hasMessageContaining("USD")
                .hasMessageContaining(PERIOD_ID.toString());
    }

    // ------------------------------------------------------------------
    // paidAt future guard
    // ------------------------------------------------------------------

    @Test
    void rejectsPaidAtInTheFuture() {
        Instant futurePaidAt = NOW.plusSeconds(60);

        assertThatThrownBy(
                () -> PaymentRegistrationPolicy.validate(
                        CLIENT_ID,
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        FINAL_PRICE,
                        CURRENCY,
                        PaymentMethod.CASH,
                        null,
                        futurePaidAt,
                        CLIENT_ID,
                        MembershipStatus.ACTIVE,
                        FINAL_PRICE,
                        CURRENCY,
                        NOW))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessage("Payment paid-at timestamp must not be in the future.");
    }

    @Test
    void acceptsPaidAtEqualToNow() {
        PaymentRegistration reg = PaymentRegistrationPolicy.validate(
                CLIENT_ID,
                MEMBERSHIP_ID,
                PERIOD_ID,
                FINAL_PRICE,
                CURRENCY,
                PaymentMethod.CASH,
                null,
                NOW,            // paidAt == now is allowed
                CLIENT_ID,
                MembershipStatus.ACTIVE,
                FINAL_PRICE,
                CURRENCY,
                NOW);

        assertThat(reg.paidAt()).isEqualTo(NOW);
    }

    @Test
    void acceptsPaidAtBeforeNow() {
        PaymentRegistration reg = validate(MembershipStatus.ACTIVE);

        assertThat(reg.paidAt()).isBefore(NOW);
    }

    // ------------------------------------------------------------------
    // Required policy input guards
    // ------------------------------------------------------------------

    @Test
    void rejectsNullMembershipClientId() {
        assertThatThrownBy(
                () -> PaymentRegistrationPolicy.validate(
                        CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                        FINAL_PRICE, CURRENCY, PaymentMethod.CASH,
                        null, PAID_AT,
                        null,                   // membershipClientId
                        MembershipStatus.ACTIVE,
                        FINAL_PRICE, CURRENCY, NOW))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Membership client identifier");
    }

    @Test
    void rejectsNullMembershipStatus() {
        assertThatThrownBy(
                () -> PaymentRegistrationPolicy.validate(
                        CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                        FINAL_PRICE, CURRENCY, PaymentMethod.CASH,
                        null, PAID_AT,
                        CLIENT_ID,
                        null,                   // membershipStatus
                        FINAL_PRICE, CURRENCY, NOW))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Membership status");
    }

    @Test
    void rejectsNullNow() {
        assertThatThrownBy(
                () -> PaymentRegistrationPolicy.validate(
                        CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                        FINAL_PRICE, CURRENCY, PaymentMethod.CASH,
                        null, PAID_AT,
                        CLIENT_ID, MembershipStatus.ACTIVE,
                        FINAL_PRICE, CURRENCY,
                        null))                  // now
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Current instant");
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private static PaymentRegistration validate(
            MembershipStatus membershipStatus) {

        return PaymentRegistrationPolicy.validate(
                CLIENT_ID,
                MEMBERSHIP_ID,
                PERIOD_ID,
                FINAL_PRICE,
                CURRENCY,
                PaymentMethod.CASH,
                null,
                PAID_AT,
                CLIENT_ID,              // membershipClientId matches
                membershipStatus,
                FINAL_PRICE,            // periodFinalPrice matches
                CURRENCY,               // periodCurrency matches
                NOW);
    }
}
