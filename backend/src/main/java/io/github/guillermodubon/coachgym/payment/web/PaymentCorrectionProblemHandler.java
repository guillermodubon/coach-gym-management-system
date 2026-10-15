package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionDataAccessException;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionNotFoundException;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionStateConflictException;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionValidationException;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionVersionConflictException;
import io.github.guillermodubon.coachgym.payment.application.PaymentRefundConflictException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PaymentCorrectionController.class)
class PaymentCorrectionProblemHandler {

    @ExceptionHandler(PaymentCorrectionValidationException.class)
    ResponseEntity<ProblemDetail> handleValidation(
            PaymentCorrectionValidationException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "PAYMENT_CORRECTION_VALIDATION_FAILED",
                exception.getMessage());
    }

    @ExceptionHandler({
        PaymentCorrectionNotFoundException.class,
        PaymentCorrectionResourceNotFoundException.class
    })
    ResponseEntity<ProblemDetail> handleNotFound(RuntimeException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "PAYMENT_NOT_FOUND",
                exception.getMessage());
    }

    @ExceptionHandler(PaymentCorrectionVersionConflictException.class)
    ResponseEntity<ProblemDetail> handleVersionConflict(
            PaymentCorrectionVersionConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "PAYMENT_VERSION_CONFLICT",
                "The payment was modified by another operation.");
    }

    @ExceptionHandler(PaymentCorrectionStateConflictException.class)
    ResponseEntity<ProblemDetail> handleStateConflict(
            PaymentCorrectionStateConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "PAYMENT_STATE_CONFLICT",
                "The payment is not in a state that permits this correction.");
    }

    @ExceptionHandler(PaymentRefundConflictException.class)
    ResponseEntity<ProblemDetail> handleRefundConflict(
            PaymentRefundConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "PAYMENT_REFUND_CONFLICT",
                "A full refund has already been registered for this payment.");
    }

    @ExceptionHandler(PaymentCorrectionDataAccessException.class)
    ResponseEntity<ProblemDetail> handleDataAccessFailure(
            PaymentCorrectionDataAccessException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "PAYMENT_CORRECTION_DATA_ACCESS_FAILED",
                "The payment correction operation could not be completed.");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail) {
        return ResponseEntity.status(status)
                .body(ApiProblemFactory.create(status, code, detail));
    }
}
