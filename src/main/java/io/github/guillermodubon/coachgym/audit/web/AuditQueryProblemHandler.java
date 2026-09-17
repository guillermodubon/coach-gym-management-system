package io.github.guillermodubon.coachgym.audit.web;

import io.github.guillermodubon.coachgym.audit.AuditQueryValidationException;
import io.github.guillermodubon.coachgym.audit.application.AuditEntryNotFoundException;
import io.github.guillermodubon.coachgym.audit.application.AuditMetadataProjectionException;
import io.github.guillermodubon.coachgym.audit.application.AuditQueryDataAccessException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Stable, privacy-safe errors for the ADMIN audit query HTTP boundary. */
@RestControllerAdvice(assignableTypes = AuditQueryController.class)
class AuditQueryProblemHandler {

    @ExceptionHandler({AuditQueryValidationException.class, IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> handleValidation(RuntimeException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "AUDIT_QUERY_VALIDATION_FAILED",
                "The audit query is invalid.");
    }

    @ExceptionHandler(AuditEntryNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(AuditEntryNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "AUDIT_ENTRY_NOT_FOUND",
                "The requested audit entry was not found.");
    }

    @ExceptionHandler(AuditQueryDataAccessException.class)
    ResponseEntity<ProblemDetail> handleDataAccess(AuditQueryDataAccessException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "AUDIT_QUERY_DATA_ACCESS_FAILED",
                "Audit entries could not be read.");
    }

    @ExceptionHandler(AuditMetadataProjectionException.class)
    ResponseEntity<ProblemDetail> handleProjection(AuditMetadataProjectionException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "AUDIT_METADATA_PROJECTION_FAILED",
                "Audit metadata could not be projected safely.");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail) {
        return ResponseEntity.status(status)
                .body(ApiProblemFactory.create(status, code, detail));
    }
}
