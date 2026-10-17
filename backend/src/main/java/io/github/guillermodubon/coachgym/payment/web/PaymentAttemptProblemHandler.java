package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptNotFoundException;
import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptVersionConflictException;
import io.github.guillermodubon.coachgym.payment.application.PaymentMembershipNotFoundException;
import io.github.guillermodubon.coachgym.payment.application.PaymentPeriodMismatchException;
import io.github.guillermodubon.coachgym.payment.application.PaymentPeriodNotFoundException;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentAttemptStateConflictException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentAttemptValidationException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentMembershipMismatchException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentMembershipStateConflictException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Stable, privacy-safe ProblemDetail mapping for staff attempt operations. */
@RestControllerAdvice(assignableTypes = PaymentAttemptController.class)
class PaymentAttemptProblemHandler {

    @ExceptionHandler(PaymentAttemptValidationException.class)
    ResponseEntity<ProblemDetail> handleValidation(PaymentAttemptValidationException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "PAYMENT_ATTEMPT_VALIDATION_FAILED",
                "One or more payment-attempt values are invalid.");
    }

    @ExceptionHandler(PaymentAttemptNotFoundException.class)
    ResponseEntity<ProblemDetail> handleAttemptNotFound(PaymentAttemptNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "PAYMENT_ATTEMPT_NOT_FOUND",
                "The payment attempt was not found.");
    }

    @ExceptionHandler({
        PaymentMembershipNotFoundException.class,
        PaymentPeriodNotFoundException.class
    })
    ResponseEntity<ProblemDetail> handleRelatedResourceNotFound(RuntimeException exception) {
        String code = exception instanceof PaymentMembershipNotFoundException
                ? "PAYMENT_MEMBERSHIP_NOT_FOUND"
                : "PAYMENT_PERIOD_NOT_FOUND";
        return problem(HttpStatus.NOT_FOUND, code, "The requested payment resource was not found.");
    }

    @ExceptionHandler({
        PaymentMembershipMismatchException.class,
        PaymentPeriodMismatchException.class
    })
    ResponseEntity<ProblemDetail> handleRelationshipConflict(RuntimeException exception) {
        String code = exception instanceof PaymentMembershipMismatchException
                ? "PAYMENT_MEMBERSHIP_MISMATCH"
                : "PAYMENT_PERIOD_MISMATCH";
        return problem(HttpStatus.CONFLICT, code, "The selected payment resources do not belong together.");
    }

    @ExceptionHandler(PaymentMembershipStateConflictException.class)
    ResponseEntity<ProblemDetail> handleMembershipStateConflict(
            PaymentMembershipStateConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "PAYMENT_MEMBERSHIP_STATE_CONFLICT",
                "The membership is not eligible for this payment attempt.");
    }

    @ExceptionHandler(PaymentAttemptStateConflictException.class)
    ResponseEntity<ProblemDetail> handleAttemptStateConflict(
            PaymentAttemptStateConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "PAYMENT_ATTEMPT_STATE_CONFLICT",
                "The payment attempt is not in a state that permits this operation.");
    }

    @ExceptionHandler(PaymentAttemptVersionConflictException.class)
    ResponseEntity<ProblemDetail> handleVersionConflict(
            PaymentAttemptVersionConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "PAYMENT_ATTEMPT_VERSION_CONFLICT",
                "The payment attempt was modified by another operation.");
    }

    @ExceptionHandler(PaymentProviderException.class)
    ResponseEntity<ProblemDetail> handleProviderFailure(PaymentProviderException exception) {
        return switch (exception.failureCode()) {
            case UNAVAILABLE -> problem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PAYMENT_PROVIDER_UNAVAILABLE",
                    "The payment provider is temporarily unavailable.");
            case TIMEOUT -> problem(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "PAYMENT_PROVIDER_TIMEOUT",
                    "The payment provider did not respond in time.");
            case INVALID_RESPONSE -> problem(
                    HttpStatus.BAD_GATEWAY,
                    "PAYMENT_PROVIDER_DATA_MISMATCH",
                    "The payment provider returned unusable checkout data.");
            case INVALID_SIGNATURE, UNSUPPORTED_EVENT -> problem(
                    HttpStatus.BAD_REQUEST,
                    "MALFORMED_PROVIDER_PAYLOAD",
                    "The provider notification could not be accepted.");
        };
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail) {
        return ResponseEntity.status(status)
                .body(ApiProblemFactory.create(status, code, detail));
    }
}
