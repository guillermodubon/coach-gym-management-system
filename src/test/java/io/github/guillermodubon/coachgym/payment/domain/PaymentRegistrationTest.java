package io.github.guillermodubon.coachgym.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentRegistrationTest {

    private static final UUID CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    private static final Instant PAID_AT =
            Instant.parse("2026-08-25T18:30:00Z");

    // ------------------------------------------------------------------
    // Valid construction and normalization
    // ------------------------------------------------------------------

    @Test
    void createsValidRegistration() {
        PaymentRegistration reg = validRegistration();

        assertThat(reg.clientId()).isEqualTo(CLIENT_ID);
        assertThat(reg.membershipId()).isEqualTo(MEMBERSHIP_ID);
        assertThat(reg.membershipPeriodId()).isEqualTo(PERIOD_ID);
        assertThat(reg.amount()).isEqualByComparingTo("25.00");
        assertThat(reg.currency()).isEqualTo("USD");
        assertThat(reg.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(reg.externalReference()).isNull();
        assertThat(reg.paidAt()).isEqualTo(PAID_AT);
    }

    @Test
    void normalizesAmountToScaleTwo_integer() {
        PaymentRegistration reg = registrationWithAmount("25");

        assertThat(reg.amount()).isEqualByComparingTo("25.00");
        assertThat(reg.amount().scale()).isEqualTo(2);
    }

    @Test
    void normalizesAmountToScaleTwo_oneDecimal() {
        PaymentRegistration reg = registrationWithAmount("25.5");

        assertThat(reg.amount()).isEqualByComparingTo("25.50");
        assertThat(reg.amount().scale()).isEqualTo(2);
    }

    @Test
    void acceptsExactlyTwoDecimals() {
        PaymentRegistration reg = registrationWithAmount("25.55");

        assertThat(reg.amount()).isEqualByComparingTo("25.55");
    }

    @Test
    void normalizesCurrencyToUpperCase() {
        PaymentRegistration reg = registrationWithCurrency("usd");

        assertThat(reg.currency()).isEqualTo("USD");
    }

    @Test
    void trimsCurrencyWhitespace() {
        PaymentRegistration reg = registrationWithCurrency("  USD  ");

        assertThat(reg.currency()).isEqualTo("USD");
    }

    @Test
    void blankExternalReferenceBecomesNull() {
        PaymentRegistration reg = registrationWithReference("   ");

        assertThat(reg.externalReference()).isNull();
    }

    @Test
    void nullExternalReferenceRemainsNull() {
        PaymentRegistration reg = registrationWithReference(null);

        assertThat(reg.externalReference()).isNull();
    }

    @Test
    void trimsNonBlankExternalReference() {
        PaymentRegistration reg = registrationWithReference("  REF-001  ");

        assertThat(reg.externalReference()).isEqualTo("REF-001");
    }

    @Test
    void acceptsExternalReferenceAtMaxLength() {
        String ref = "X".repeat(128);
        PaymentRegistration reg = registrationWithReference(ref);

        assertThat(reg.externalReference()).hasSize(128);
    }

    // ------------------------------------------------------------------
    // Amount rejections
    // ------------------------------------------------------------------

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(
                () -> new PaymentRegistration(
                        CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                        null, "USD", PaymentMethod.CASH, null, PAID_AT))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessage("Payment amount must be provided.");
    }

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(
                () -> registrationWithAmount("0.00"))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessage("Payment amount must be greater than zero.");
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(
                () -> registrationWithAmount("-10.00"))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessage("Payment amount must be greater than zero.");
    }

    @Test
    void rejectsThreeDecimalPlaces() {
        assertThatThrownBy(
                () -> registrationWithAmount("25.555"))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessage("Payment amount must not exceed two decimal places.");
    }

    @Test
    void rejectsManyDecimalPlaces() {
        assertThatThrownBy(
                () -> registrationWithAmount("25.001"))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessage("Payment amount must not exceed two decimal places.");
    }

    // ------------------------------------------------------------------
    // Currency rejections
    // ------------------------------------------------------------------

    @Test
    void rejectsNullCurrency() {
        assertThatThrownBy(
                () -> new PaymentRegistration(
                        CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                        new BigDecimal("25.00"), null, PaymentMethod.CASH,
                        null, PAID_AT))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessage("Payment currency must be provided.");
    }

    @Test
    void rejectsBlankCurrency() {
        assertThatThrownBy(
                () -> registrationWithCurrency("   "))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessage("Payment currency must be provided.");
    }

    @Test
    void rejectsTwoLetterCurrency() {
        assertThatThrownBy(
                () -> registrationWithCurrency("US"))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessage("Payment currency must be a three-letter ISO code.");
    }

    @Test
    void rejectsFourLetterCurrency() {
        assertThatThrownBy(
                () -> registrationWithCurrency("USDD"))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessage("Payment currency must be a three-letter ISO code.");
    }

    @Test
    void rejectsCurrencyWithDigits() {
        assertThatThrownBy(
                () -> registrationWithCurrency("U5D"))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessage("Payment currency must be a three-letter ISO code.");
    }

    // ------------------------------------------------------------------
    // Required field rejections
    // ------------------------------------------------------------------

    @Test
    void rejectsNullClientId() {
        assertThatThrownBy(
                () -> new PaymentRegistration(
                        null, MEMBERSHIP_ID, PERIOD_ID,
                        new BigDecimal("25.00"), "USD", PaymentMethod.CASH,
                        null, PAID_AT))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessage("Payment client identifier must be provided.");
    }

    @Test
    void rejectsNullMembershipId() {
        assertThatThrownBy(
                () -> new PaymentRegistration(
                        CLIENT_ID, null, PERIOD_ID,
                        new BigDecimal("25.00"), "USD", PaymentMethod.CASH,
                        null, PAID_AT))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessage("Payment membership identifier must be provided.");
    }

    @Test
    void rejectsNullMembershipPeriodId() {
        assertThatThrownBy(
                () -> new PaymentRegistration(
                        CLIENT_ID, MEMBERSHIP_ID, null,
                        new BigDecimal("25.00"), "USD", PaymentMethod.CASH,
                        null, PAID_AT))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessage("Payment membership period identifier must be provided.");
    }

    @Test
    void rejectsNullPaymentMethod() {
        assertThatThrownBy(
                () -> new PaymentRegistration(
                        CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                        new BigDecimal("25.00"), "USD", null,
                        null, PAID_AT))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessage("Payment method must be provided.");
    }

    @Test
    void rejectsNullPaidAt() {
        assertThatThrownBy(
                () -> new PaymentRegistration(
                        CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                        new BigDecimal("25.00"), "USD", PaymentMethod.CASH,
                        null, null))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessage("Payment paid-at timestamp must be provided.");
    }

    @Test
    void rejectsExternalReferenceTooLong() {
        String tooLong = "X".repeat(129);

        assertThatThrownBy(
                () -> registrationWithReference(tooLong))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("128");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static PaymentRegistration validRegistration() {
        return new PaymentRegistration(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                new BigDecimal("25.00"), "USD", PaymentMethod.CASH,
                null, PAID_AT);
    }

    private static PaymentRegistration registrationWithAmount(
            String amount) {

        return new PaymentRegistration(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                new BigDecimal(amount), "USD", PaymentMethod.CASH,
                null, PAID_AT);
    }

    private static PaymentRegistration registrationWithCurrency(
            String currency) {

        return new PaymentRegistration(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                new BigDecimal("25.00"), currency, PaymentMethod.CASH,
                null, PAID_AT);
    }

    private static PaymentRegistration registrationWithReference(
            String reference) {

        return new PaymentRegistration(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                new BigDecimal("25.00"), "USD", PaymentMethod.CASH,
                reference, PAID_AT);
    }
}
