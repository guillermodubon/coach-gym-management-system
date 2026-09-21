package io.github.guillermodubon.coachgym.organization.web;

import io.github.guillermodubon.coachgym.organization.GymBranchStateConflictException;
import io.github.guillermodubon.coachgym.organization.GymBranchValidationException;
import io.github.guillermodubon.coachgym.organization.OrganizationStateConflictException;
import io.github.guillermodubon.coachgym.organization.OrganizationValidationException;
import io.github.guillermodubon.coachgym.organization.application.GymBranchCodeConflictException;
import io.github.guillermodubon.coachgym.organization.application.GymBranchDataAccessException;
import io.github.guillermodubon.coachgym.organization.application.GymBranchNotFoundException;
import io.github.guillermodubon.coachgym.organization.application.GymBranchVersionConflictException;
import io.github.guillermodubon.coachgym.organization.application.OrganizationDataAccessException;
import io.github.guillermodubon.coachgym.organization.application.OrganizationNotFoundException;
import io.github.guillermodubon.coachgym.organization.application.OrganizationVersionConflictException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** Privacy-safe HTTP errors for organization and branch APIs. */
@RestControllerAdvice(assignableTypes = {OrganizationController.class, GymBranchController.class})
class OrganizationProblemHandler {

    @ExceptionHandler({
            OrganizationValidationException.class,
            GymBranchValidationException.class,
            IllegalArgumentException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> handleValidation(
            Exception exception,
            HttpServletRequest request) {
        String code = exception instanceof GymBranchValidationException
                || request.getRequestURI().startsWith("/api/v1/branches")
                ? "GYM_BRANCH_VALIDATION_FAILED"
                : "ORGANIZATION_VALIDATION_FAILED";
        String detail = code.startsWith("GYM_BRANCH")
                ? "The gym branch request is invalid."
                : "The organization request is invalid.";
        return problem(HttpStatus.BAD_REQUEST, code, detail);
    }

    @ExceptionHandler(OrganizationNotFoundException.class)
    ResponseEntity<ProblemDetail> handleOrganizationNotFound(OrganizationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND",
                "The canonical organization was not found.");
    }

    @ExceptionHandler(OrganizationVersionConflictException.class)
    ResponseEntity<ProblemDetail> handleOrganizationVersion(OrganizationVersionConflictException exception) {
        return problem(HttpStatus.CONFLICT, "ORGANIZATION_VERSION_CONFLICT",
                "The organization was modified by another operation.");
    }

    @ExceptionHandler(OrganizationStateConflictException.class)
    ResponseEntity<ProblemDetail> handleOrganizationState(OrganizationStateConflictException exception) {
        return problem(HttpStatus.CONFLICT, "ORGANIZATION_STATE_CONFLICT",
                "The organization lifecycle transition is not allowed.");
    }

    @ExceptionHandler(OrganizationDataAccessException.class)
    ResponseEntity<ProblemDetail> handleOrganizationData(OrganizationDataAccessException exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "ORGANIZATION_DATA_ACCESS_FAILED",
                "The organization operation could not be completed.");
    }

    @ExceptionHandler(GymBranchNotFoundException.class)
    ResponseEntity<ProblemDetail> handleBranchNotFound(GymBranchNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "GYM_BRANCH_NOT_FOUND",
                "The requested gym branch was not found.");
    }

    @ExceptionHandler(GymBranchCodeConflictException.class)
    ResponseEntity<ProblemDetail> handleBranchCode(GymBranchCodeConflictException exception) {
        return problem(HttpStatus.CONFLICT, "GYM_BRANCH_CODE_CONFLICT",
                "A gym branch with that code already exists.");
    }

    @ExceptionHandler(GymBranchVersionConflictException.class)
    ResponseEntity<ProblemDetail> handleBranchVersion(GymBranchVersionConflictException exception) {
        return problem(HttpStatus.CONFLICT, "GYM_BRANCH_VERSION_CONFLICT",
                "The gym branch was modified by another operation.");
    }

    @ExceptionHandler(GymBranchStateConflictException.class)
    ResponseEntity<ProblemDetail> handleBranchState(GymBranchStateConflictException exception) {
        boolean initialProtected = exception.getMessage() != null
                && exception.getMessage().toLowerCase(java.util.Locale.ROOT).contains("initial branch");
        return problem(HttpStatus.CONFLICT,
                initialProtected ? "GYM_BRANCH_INITIAL_PROTECTED" : "GYM_BRANCH_STATE_CONFLICT",
                initialProtected
                        ? "The only active initial branch cannot be deactivated."
                        : "The gym branch lifecycle transition is not allowed.");
    }

    @ExceptionHandler(GymBranchDataAccessException.class)
    ResponseEntity<ProblemDetail> handleBranchData(GymBranchDataAccessException exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "GYM_BRANCH_DATA_ACCESS_FAILED",
                "The gym branch operation could not be completed.");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail) {
        return ResponseEntity.status(status).body(ApiProblemFactory.create(status, code, detail));
    }
}
