package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import io.github.guillermodubon.coachgym.user.ActiveBranchContextUnavailableException;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationException;
import io.github.guillermodubon.coachgym.user.BranchResourceMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** HTTP denial mappings for the shared branch authorization contract. */
@RestControllerAdvice
class BranchResourceProblemHandler {

    @ExceptionHandler(ActiveBranchContextUnavailableException.class)
    ResponseEntity<ProblemDetail> handleUnavailableActiveBranch(
            ActiveBranchContextUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiProblemFactory.create(
                        HttpStatus.CONFLICT,
                        "ACTIVE_BRANCH_UNAVAILABLE",
                        "Select an active branch before performing this operation."));
    }

    @ExceptionHandler(BranchResourceAuthorizationException.class)
    ResponseEntity<ProblemDetail> handleBranchResourceAuthorization(
            BranchResourceAuthorizationException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiProblemFactory.create(
                        HttpStatus.NOT_FOUND,
                        "RESOURCE_NOT_FOUND",
                        "The requested resource was not found."));
    }

    @ExceptionHandler(BranchResourceMismatchException.class)
    ResponseEntity<ProblemDetail> handleBranchResourceMismatch(
            BranchResourceMismatchException exception) {
        return ResponseEntity.badRequest()
                .body(ApiProblemFactory.create(
                        HttpStatus.BAD_REQUEST,
                        "BRANCH_CONTEXT_MISMATCH",
                        "The requested branch does not match the active branch context."));
    }
}
