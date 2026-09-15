package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.application.PaymentNotFoundException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptDataAccessException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptDuplicateException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptNotFoundException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptRenderException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStateConflictException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStorageException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptValidationException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Stable, privacy-safe ProblemDetail mapping for payment receipt operations. */
@RestControllerAdvice(assignableTypes = PaymentReceiptController.class)
class PaymentReceiptProblemHandler {

    @ExceptionHandler(PaymentReceiptValidationException.class)
    ResponseEntity<ProblemDetail> handleValidation(PaymentReceiptValidationException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "PAYMENT_RECEIPT_VALIDATION_FAILED",
                "The payment receipt request is invalid.");
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ResponseEntity<ProblemDetail> handlePaymentNotFound(PaymentNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "PAYMENT_NOT_FOUND",
                "The requested payment was not found.");
    }

    @ExceptionHandler(PaymentReceiptNotFoundException.class)
    ResponseEntity<ProblemDetail> handleReceiptNotFound(PaymentReceiptNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "PAYMENT_RECEIPT_NOT_FOUND",
                "The requested payment receipt was not found.");
    }

    @ExceptionHandler(PaymentReceiptStateConflictException.class)
    ResponseEntity<ProblemDetail> handleStateConflict(PaymentReceiptStateConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "PAYMENT_RECEIPT_STATE_CONFLICT",
                "A receipt can only be generated for a confirmed paid payment.");
    }

    @ExceptionHandler(PaymentReceiptDuplicateException.class)
    ResponseEntity<ProblemDetail> handleDuplicate(PaymentReceiptDuplicateException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "PAYMENT_RECEIPT_ALREADY_EXISTS",
                "A canonical payment receipt already exists.");
    }

    @ExceptionHandler(PaymentReceiptRenderException.class)
    ResponseEntity<ProblemDetail> handleRenderFailure(PaymentReceiptRenderException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "PAYMENT_RECEIPT_RENDER_FAILED",
                "The payment receipt could not be rendered.");
    }

    @ExceptionHandler(PaymentReceiptStorageException.class)
    ResponseEntity<ProblemDetail> handleStorageFailure(PaymentReceiptStorageException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "PAYMENT_RECEIPT_STORAGE_FAILED",
                "The payment receipt document is temporarily unavailable.");
    }

    @ExceptionHandler(PaymentReceiptDataAccessException.class)
    ResponseEntity<ProblemDetail> handleDataAccessFailure(PaymentReceiptDataAccessException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "PAYMENT_RECEIPT_DATA_ACCESS_FAILED",
                "The payment receipt operation could not be completed.");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail) {
        return ResponseEntity.status(status)
                .body(ApiProblemFactory.create(status, code, detail));
    }
}
