package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import io.github.guillermodubon.coachgym.user.StaffBranchAuthorizationException;
import io.github.guillermodubon.coachgym.user.StaffIdentityAuthorizationException;
import io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffIdentityValidationException;
import io.github.guillermodubon.coachgym.user.StaffInvitationNotFoundException;
import io.github.guillermodubon.coachgym.user.StaffInvitationRateLimitException;
import io.github.guillermodubon.coachgym.user.application.StaffCurrentPasswordInvalidException;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityDataAccessException;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityNotFoundException;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityVersionConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Stable ProblemDetail mapping for identity endpoints; it never includes exception data. */
@RestControllerAdvice(assignableTypes = {
        StaffInvitationController.class,
        StaffIdentityAdministrationController.class,
        PasswordRecoveryController.class})
class StaffIdentityProblemHandler {

    private final HttpServletRequest request;

    StaffIdentityProblemHandler(HttpServletRequest request) {
        this.request = request;
    }

    @ExceptionHandler({
            StaffIdentityValidationException.class,
            IllegalArgumentException.class,
            MethodArgumentNotValidException.class,
            TypeMismatchException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> invalidRequest(Exception ignored) {
        return problem(HttpStatus.BAD_REQUEST, "STAFF_IDENTITY_REQUEST_INVALID",
                "The staff identity request is invalid.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> malformedRequest(HttpMessageNotReadableException ignored) {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body is malformed or contains unsupported fields.");
    }

    @ExceptionHandler({
            StaffIdentityAuthorizationException.class,
            StaffBranchAuthorizationException.class,
            StaffCurrentPasswordInvalidException.class})
    ResponseEntity<ProblemDetail> forbidden(Exception ignored) {
        return problem(HttpStatus.FORBIDDEN, "STAFF_IDENTITY_OPERATION_FORBIDDEN",
                "The authenticated staff scope cannot perform this operation.");
    }

    @ExceptionHandler({StaffIdentityNotFoundException.class, StaffInvitationNotFoundException.class})
    ResponseEntity<ProblemDetail> notFound(RuntimeException ignored) {
        return problem(HttpStatus.NOT_FOUND, "STAFF_IDENTITY_NOT_FOUND",
                "The staff identity resource was not found.");
    }

    @ExceptionHandler(StaffInvitationRateLimitException.class)
    ResponseEntity<ProblemDetail> rateLimited(StaffInvitationRateLimitException ignored) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "STAFF_IDENTITY_RATE_LIMITED",
                "The staff identity operation is temporarily unavailable. Try again later.");
    }

    @ExceptionHandler(StaffIdentityStateConflictException.class)
    ResponseEntity<ProblemDetail> stateConflict(StaffIdentityStateConflictException exception) {
        String detail = request.getRequestURI().contains("password-recovery")
                ? "Password recovery request is not available."
                : request.getRequestURI().endsWith("/inspect")
                        || request.getRequestURI().endsWith("/accept")
                        ? "Invitation is not available."
                        : "The staff identity cannot be changed in its current state.";
        return problem(HttpStatus.CONFLICT,
                request.getRequestURI().contains("password-recovery")
                        ? "PASSWORD_RECOVERY_NOT_AVAILABLE"
                        : request.getRequestURI().endsWith("/inspect")
                                || request.getRequestURI().endsWith("/accept")
                                ? "INVITATION_NOT_AVAILABLE"
                                : "STAFF_IDENTITY_CONFLICT",
                detail);
    }

    @ExceptionHandler(StaffIdentityVersionConflictException.class)
    ResponseEntity<ProblemDetail> versionConflict(StaffIdentityVersionConflictException ignored) {
        return problem(HttpStatus.CONFLICT, "STAFF_IDENTITY_VERSION_CONFLICT",
                "The staff identity changed. Reload it and retry.");
    }

    @ExceptionHandler(StaffIdentityDataAccessException.class)
    ResponseEntity<ProblemDetail> persistenceFailure(StaffIdentityDataAccessException ignored) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "STAFF_IDENTITY_OPERATION_FAILED",
                "The staff identity operation could not be completed.");
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail) {
        ProblemDetail body = ApiProblemFactory.create(status, code, detail);
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("Referrer-Policy", "no-referrer")
                .body(body);
    }
}
