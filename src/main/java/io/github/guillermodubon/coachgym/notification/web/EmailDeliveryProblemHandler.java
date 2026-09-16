package io.github.guillermodubon.coachgym.notification.web;

import io.github.guillermodubon.coachgym.notification.application.EmailCompositionException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryAttachmentException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryDataAccessException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryDuplicateException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryNotFoundException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryRecipientUnavailableException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryRetryLimitExceededException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliverySourceNotFoundException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryStateConflictException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryVersionConflictException;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValidationException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Stable, privacy-safe ProblemDetail mapping for transactional email APIs. */
@RestControllerAdvice(assignableTypes = EmailDeliveryController.class)
class EmailDeliveryProblemHandler {

    @ExceptionHandler({EmailDeliveryValidationException.class, IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> handleValidation(RuntimeException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "EMAIL_DELIVERY_VALIDATION_FAILED",
                "The email delivery request is invalid.");
    }

    @ExceptionHandler(EmailDeliveryNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(EmailDeliveryNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "EMAIL_DELIVERY_NOT_FOUND",
                "The requested email delivery was not found.");
    }

    @ExceptionHandler(EmailDeliverySourceNotFoundException.class)
    ResponseEntity<ProblemDetail> handleSourceNotFound(EmailDeliverySourceNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "EMAIL_DELIVERY_SOURCE_NOT_FOUND",
                "The canonical email source was not found.");
    }

    @ExceptionHandler(EmailDeliveryRecipientUnavailableException.class)
    ResponseEntity<ProblemDetail> handleRecipientUnavailable(
            EmailDeliveryRecipientUnavailableException exception) {
        return problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "EMAIL_DELIVERY_RECIPIENT_UNAVAILABLE",
                "The authoritative client recipient is unavailable.");
    }

    @ExceptionHandler(EmailDeliveryAttachmentException.class)
    ResponseEntity<ProblemDetail> handleAttachment(EmailDeliveryAttachmentException exception) {
        return problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "EMAIL_DELIVERY_ATTACHMENT_UNAVAILABLE",
                "The canonical email attachment is unavailable.");
    }

    @ExceptionHandler(EmailDeliveryDuplicateException.class)
    ResponseEntity<ProblemDetail> handleDuplicate(EmailDeliveryDuplicateException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "EMAIL_DELIVERY_DUPLICATE",
                "The canonical email delivery already exists.");
    }

    @ExceptionHandler(EmailDeliveryStateConflictException.class)
    ResponseEntity<ProblemDetail> handleStateConflict(EmailDeliveryStateConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "EMAIL_DELIVERY_STATE_CONFLICT",
                "The email delivery cannot be changed from its current state.");
    }

    @ExceptionHandler(EmailDeliveryVersionConflictException.class)
    ResponseEntity<ProblemDetail> handleVersionConflict(EmailDeliveryVersionConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "EMAIL_DELIVERY_VERSION_CONFLICT",
                "The email delivery was modified by another operation.");
    }

    @ExceptionHandler(EmailDeliveryRetryLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleRetryLimit(EmailDeliveryRetryLimitExceededException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "EMAIL_DELIVERY_RETRY_LIMIT_REACHED",
                "The email delivery retry limit has been reached.");
    }

    @ExceptionHandler({EmailCompositionException.class, EmailDeliveryDataAccessException.class})
    ResponseEntity<ProblemDetail> handleInfrastructureFailure(RuntimeException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "EMAIL_DELIVERY_OPERATION_FAILED",
                "The email delivery operation could not be completed.");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail) {
        return ResponseEntity.status(status)
                .body(ApiProblemFactory.create(status, code, detail));
    }
}
