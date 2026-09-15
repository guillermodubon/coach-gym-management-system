package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.application.PaymentProviderException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Safe provider-facing errors; provider messages never cross the HTTP boundary. */
@RestControllerAdvice(assignableTypes = StripeWebhookController.class)
class StripeWebhookProblemHandler {

    @ExceptionHandler(PaymentWebhookPayloadTooLargeException.class)
    ResponseEntity<ProblemDetail> handlePayloadTooLarge(PaymentWebhookPayloadTooLargeException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "WEBHOOK_PAYLOAD_TOO_LARGE",
                "The provider notification body exceeds the permitted size.");
    }

    @ExceptionHandler(PaymentWebhookSignatureTooLargeException.class)
    ResponseEntity<ProblemDetail> handleSignatureTooLarge(
            PaymentWebhookSignatureTooLargeException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "WEBHOOK_SIGNATURE_TOO_LARGE",
                "The provider signature header exceeds the permitted size.");
    }

    @ExceptionHandler(PaymentProviderException.class)
    ResponseEntity<ProblemDetail> handleProviderFailure(PaymentProviderException exception) {
        return switch (exception.failureCode()) {
            case INVALID_SIGNATURE -> problem(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_WEBHOOK_SIGNATURE",
                    "The provider signature is missing, malformed, invalid, or stale.");
            case INVALID_RESPONSE, UNSUPPORTED_EVENT -> problem(
                    HttpStatus.BAD_REQUEST,
                    "MALFORMED_PROVIDER_PAYLOAD",
                    "The provider notification is malformed or unsupported.");
            case UNAVAILABLE -> problem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PAYMENT_PROVIDER_UNAVAILABLE",
                    "Provider verification is temporarily unavailable.");
            case TIMEOUT -> problem(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "PAYMENT_PROVIDER_TIMEOUT",
                    "Provider verification timed out.");
        };
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ProblemDetail> handleRetryableProcessing(IllegalStateException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "PAYMENT_PROVIDER_PROCESSING_RETRYABLE",
                "The provider notification could not be completed and may be retried.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleMalformedRequest(HttpMessageNotReadableException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_PROVIDER_PAYLOAD",
                "The provider notification is malformed or missing.");
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ProblemDetail> handleUnexpectedProcessingFailure(RuntimeException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "PAYMENT_PROVIDER_PROCESSING_RETRYABLE",
                "The provider notification could not be completed and may be retried.");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail) {
        return ResponseEntity.status(status)
                .body(ApiProblemFactory.create(status, code, detail));
    }
}
