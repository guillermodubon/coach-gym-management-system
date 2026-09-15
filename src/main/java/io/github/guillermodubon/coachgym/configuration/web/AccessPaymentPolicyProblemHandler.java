package io.github.guillermodubon.coachgym.configuration.web;

import io.github.guillermodubon.coachgym.configuration.application.AccessPaymentPolicyDataAccessException;
import io.github.guillermodubon.coachgym.configuration.application.AccessPaymentPolicyVersionConflictException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Stable, privacy-safe ProblemDetail mapping for policy administration. */
@RestControllerAdvice(assignableTypes = AccessPaymentPolicyController.class)
class AccessPaymentPolicyProblemHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "ACCESS_PAYMENT_POLICY_VALIDATION_FAILED",
                "The access-payment policy request is invalid.");
    }

    @ExceptionHandler(AccessPaymentPolicyVersionConflictException.class)
    ResponseEntity<ProblemDetail> handleVersionConflict(
            AccessPaymentPolicyVersionConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "ACCESS_PAYMENT_POLICY_VERSION_CONFLICT",
                "The access-payment policy was modified by another operation.");
    }

    @ExceptionHandler(AccessPaymentPolicyDataAccessException.class)
    ResponseEntity<ProblemDetail> handleDataAccessFailure(
            AccessPaymentPolicyDataAccessException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "ACCESS_PAYMENT_POLICY_DATA_ACCESS_FAILED",
                "The access-payment policy operation could not be completed.");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail) {
        return ResponseEntity.status(status).body(ApiProblemFactory.create(status, code, detail));
    }
}
