package io.github.guillermodubon.coachgym.payment.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.application.DuplicatePaymentReferenceException;
import io.github.guillermodubon.coachgym.payment.application.PaymentApplicationService;
import io.github.guillermodubon.coachgym.payment.application.PaymentMembershipNotFoundException;
import io.github.guillermodubon.coachgym.payment.application.PaymentNotFoundException;
import io.github.guillermodubon.coachgym.payment.application.PaymentPeriodMismatchException;
import io.github.guillermodubon.coachgym.payment.application.PaymentPeriodNotFoundException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentAmountMismatchException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentCurrencyMismatchException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentMembershipMismatchException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentMembershipStateConflictException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentValidationException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class PaymentErrorMappingTest {

    private static final UUID PAYMENT_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    private static final UUID CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Mock
    private PaymentApplicationService paymentApplicationService;

    private PaymentController controller;

    @BeforeEach
    void setUp() {
        controller = new PaymentController(paymentApplicationService);
    }

    // ------------------------------------------------------------------
    // 400 Bad Request
    // ------------------------------------------------------------------

    @Test
    void mapsValidationExceptionTo400() {
        ResponseEntity<ProblemDetail> response =
                controller.handleValidation(
                        new PaymentValidationException("Amount must be positive."));

        assertStatus(response, HttpStatus.BAD_REQUEST);
        assertCode(response, "PAYMENT_VALIDATION_FAILED");
    }

    // ------------------------------------------------------------------
    // 404 Not Found
    // ------------------------------------------------------------------

    @Test
    void mapsPaymentNotFoundTo404() {
        ResponseEntity<ProblemDetail> response =
                controller.handlePaymentNotFound(
                        new PaymentNotFoundException(PAYMENT_ID));

        assertStatus(response, HttpStatus.NOT_FOUND);
        assertCode(response, "PAYMENT_NOT_FOUND");
    }

    @Test
    void mapsMembershipNotFoundTo404() {
        ResponseEntity<ProblemDetail> response =
                controller.handleMembershipNotFound(
                        new PaymentMembershipNotFoundException(MEMBERSHIP_ID));

        assertStatus(response, HttpStatus.NOT_FOUND);
        assertCode(response, "PAYMENT_MEMBERSHIP_NOT_FOUND");
    }

    @Test
    void mapsPeriodNotFoundTo404() {
        ResponseEntity<ProblemDetail> response =
                controller.handlePeriodNotFound(
                        new PaymentPeriodNotFoundException(PERIOD_ID));

        assertStatus(response, HttpStatus.NOT_FOUND);
        assertCode(response, "PAYMENT_PERIOD_NOT_FOUND");
    }

    // ------------------------------------------------------------------
    // 409 Conflict
    // ------------------------------------------------------------------

    @Test
    void mapsMembershipMismatchTo409() {
        ResponseEntity<ProblemDetail> response =
                controller.handleMembershipMismatch(
                        new PaymentMembershipMismatchException(CLIENT_ID, MEMBERSHIP_ID));

        assertStatus(response, HttpStatus.CONFLICT);
        assertCode(response, "PAYMENT_MEMBERSHIP_MISMATCH");
    }

    @Test
    void mapsPeriodMismatchTo409() {
        ResponseEntity<ProblemDetail> response =
                controller.handlePeriodMismatch(
                        new PaymentPeriodMismatchException(MEMBERSHIP_ID, PERIOD_ID));

        assertStatus(response, HttpStatus.CONFLICT);
        assertCode(response, "PAYMENT_PERIOD_MISMATCH");
    }

    @Test
    void mapsMembershipStateConflictTo409() {
        ResponseEntity<ProblemDetail> response =
                controller.handleMembershipStateConflict(
                        new PaymentMembershipStateConflictException(
                                MEMBERSHIP_ID, MembershipStatus.CANCELLED));

        assertStatus(response, HttpStatus.CONFLICT);
        assertCode(response, "PAYMENT_MEMBERSHIP_STATE_CONFLICT");
    }

    @Test
    void mapsAmountMismatchTo409() {
        ResponseEntity<ProblemDetail> response =
                controller.handleAmountMismatch(
                        new PaymentAmountMismatchException(
                                MEMBERSHIP_ID, PERIOD_ID,
                                new BigDecimal("20.00"),
                                new BigDecimal("25.00")));

        assertStatus(response, HttpStatus.CONFLICT);
        assertCode(response, "PAYMENT_AMOUNT_MISMATCH");
    }

    @Test
    void mapsCurrencyMismatchTo409() {
        ResponseEntity<ProblemDetail> response =
                controller.handleCurrencyMismatch(
                        new PaymentCurrencyMismatchException(
                                MEMBERSHIP_ID, PERIOD_ID, "EUR", "USD"));

        assertStatus(response, HttpStatus.CONFLICT);
        assertCode(response, "PAYMENT_CURRENCY_MISMATCH");
    }

    @Test
    void mapsDuplicateReferenceTo409() {
        ResponseEntity<ProblemDetail> response =
                controller.handleDuplicateReference(
                        new DuplicatePaymentReferenceException(
                                PaymentMethod.CARD, "REF-001"));

        assertStatus(response, HttpStatus.CONFLICT);
        assertCode(response, "DUPLICATE_PAYMENT_REFERENCE");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void assertStatus(
            ResponseEntity<ProblemDetail> response,
            HttpStatus expected) {

        assertThat(response.getStatusCode()).isEqualTo(expected);
    }

    private static void assertCode(
            ResponseEntity<ProblemDetail> response,
            String expectedCode) {

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("code", expectedCode);
    }
}
