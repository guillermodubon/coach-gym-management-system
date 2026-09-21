package io.github.guillermodubon.coachgym.accesscredential.web;

import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialClientNotFoundException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialDataAccessException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialDuplicateException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialEligibilityException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialNotFoundException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialRenderException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStateConflictException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStorageException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialTokenGenerationException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialValidationException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialVersionConflictException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Stable, privacy-safe ProblemDetail mapping for access-credential operations. */
@RestControllerAdvice(assignableTypes = AccessCredentialController.class)
class AccessCredentialProblemHandler {

    @ExceptionHandler(AccessCredentialValidationException.class)
    ResponseEntity<ProblemDetail> handleValidation(AccessCredentialValidationException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "ACCESS_CREDENTIAL_VALIDATION_FAILED",
                "The access credential request is invalid.");
    }

    @ExceptionHandler(AccessCredentialClientNotFoundException.class)
    ResponseEntity<ProblemDetail> handleClientNotFound(AccessCredentialClientNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "CLIENT_NOT_FOUND",
                "The requested client was not found.");
    }

    @ExceptionHandler(AccessCredentialNotFoundException.class)
    ResponseEntity<ProblemDetail> handleCredentialNotFound(AccessCredentialNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "ACCESS_CREDENTIAL_NOT_FOUND",
                "The requested access credential was not found.");
    }

    @ExceptionHandler(AccessCredentialDuplicateException.class)
    ResponseEntity<ProblemDetail> handleDuplicate(AccessCredentialDuplicateException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "ACCESS_CREDENTIAL_ALREADY_ACTIVE",
                "The client already has an active access credential.");
    }

    @ExceptionHandler({AccessCredentialEligibilityException.class,
            AccessCredentialStateConflictException.class})
    ResponseEntity<ProblemDetail> handleStateConflict(RuntimeException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "ACCESS_CREDENTIAL_STATE_CONFLICT",
                "The access credential cannot be changed from its current state.");
    }

    @ExceptionHandler(AccessCredentialVersionConflictException.class)
    ResponseEntity<ProblemDetail> handleVersionConflict(AccessCredentialVersionConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "ACCESS_CREDENTIAL_VERSION_CONFLICT",
                "The access credential was changed by another request.");
    }

    @ExceptionHandler(AccessCredentialTokenGenerationException.class)
    ResponseEntity<ProblemDetail> handleTokenGeneration(AccessCredentialTokenGenerationException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "ACCESS_CREDENTIAL_TOKEN_GENERATION_FAILED",
                "The access credential could not be generated.");
    }

    @ExceptionHandler(AccessCredentialRenderException.class)
    ResponseEntity<ProblemDetail> handleRender(AccessCredentialRenderException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "ACCESS_CREDENTIAL_RENDER_FAILED",
                "The access credential image could not be rendered.");
    }

    @ExceptionHandler(AccessCredentialStorageException.class)
    ResponseEntity<ProblemDetail> handleStorage(AccessCredentialStorageException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "ACCESS_CREDENTIAL_STORAGE_FAILED",
                "The access credential document is temporarily unavailable.");
    }

    @ExceptionHandler(AccessCredentialDataAccessException.class)
    ResponseEntity<ProblemDetail> handleDataAccess(AccessCredentialDataAccessException exception) {
        if (isConcurrencyFailure(exception)) {
            return problem(
                    HttpStatus.CONFLICT,
                    "ACCESS_CREDENTIAL_VERSION_CONFLICT",
                    "The access credential was changed by another request.");
        }
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "ACCESS_CREDENTIAL_DATA_ACCESS_FAILED",
                "The access credential operation could not be completed.");
    }

    /**
     * PostgreSQL can report a lock timeout, serialization, or deadlock failure
     * while the outer transaction is committing, after the application
     * service has already returned. Translate only those transient lock
     * failures to the same stable optimistic-concurrency response used by
     * row-level checks.
     */
    @ExceptionHandler(TransactionSystemException.class)
    ResponseEntity<ProblemDetail> handleTransactionFailure(
            TransactionSystemException exception) {
        if (isConcurrencyFailure(exception)) {
            return problem(
                    HttpStatus.CONFLICT,
                    "ACCESS_CREDENTIAL_VERSION_CONFLICT",
                    "The access credential was changed by another request.");
        }
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "ACCESS_CREDENTIAL_DATA_ACCESS_FAILED",
                "The access credential operation could not be completed.");
    }

    @ExceptionHandler({CannotAcquireLockException.class,
            OptimisticLockingFailureException.class,
            jakarta.persistence.LockTimeoutException.class,
            jakarta.persistence.PessimisticLockException.class,
            jakarta.persistence.OptimisticLockException.class})
    ResponseEntity<ProblemDetail> handleConcurrencyFailure(RuntimeException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "ACCESS_CREDENTIAL_VERSION_CONFLICT",
                "The access credential was changed by another request.");
    }

    private static boolean isConcurrencyFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof CannotAcquireLockException
                    || current instanceof OptimisticLockingFailureException
                    || current instanceof jakarta.persistence.LockTimeoutException
                    || current instanceof jakarta.persistence.PessimisticLockException
                    || current instanceof jakarta.persistence.OptimisticLockException) {
                return true;
            }
            if (current instanceof java.sql.SQLException sqlException
                    && ("40P01".equals(sqlException.getSQLState())
                    || "40001".equals(sqlException.getSQLState())
                    || "55P03".equals(sqlException.getSQLState()))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail) {
        return ResponseEntity.status(status)
                .body(ApiProblemFactory.create(status, code, detail));
    }
}
