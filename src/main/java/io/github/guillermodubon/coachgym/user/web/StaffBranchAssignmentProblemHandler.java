package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import io.github.guillermodubon.coachgym.user.ActiveBranchContextUnavailableException;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentValidationException;
import io.github.guillermodubon.coachgym.user.StaffBranchAuthorizationException;
import io.github.guillermodubon.coachgym.user.StaffScopeStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffScopeValidationException;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentDataAccessException;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentDuplicateException;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentNotFoundException;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentVersionConflictException;
import io.github.guillermodubon.coachgym.user.application.StaffScopeDataAccessException;
import io.github.guillermodubon.coachgym.user.application.StaffScopeNotFoundException;
import io.github.guillermodubon.coachgym.user.application.StaffScopeVersionConflictException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Stable, privacy-safe errors for staff assignment and branch-context APIs. */
@RestControllerAdvice(assignableTypes = {
        StaffBranchAssignmentController.class,
        StaffBranchContextController.class})
class StaffBranchAssignmentProblemHandler {

    @ExceptionHandler({
            StaffBranchAssignmentValidationException.class,
            StaffScopeValidationException.class,
            IllegalArgumentException.class,
            MethodArgumentNotValidException.class,
            TypeMismatchException.class})
    ResponseEntity<ProblemDetail> validation(Exception exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "STAFF_BRANCH_REQUEST_INVALID",
                "Staff branch request is invalid",
                "The staff branch request is invalid.");
    }

    @ExceptionHandler(StaffBranchAuthorizationException.class)
    ResponseEntity<ProblemDetail> authorization(StaffBranchAuthorizationException exception) {
        return problem(
                HttpStatus.FORBIDDEN,
                "STAFF_BRANCH_OPERATION_FORBIDDEN",
                "Staff branch operation forbidden",
                "The authenticated staff scope cannot perform this operation.");
    }

    @ExceptionHandler(StaffBranchAssignmentNotFoundException.class)
    ResponseEntity<ProblemDetail> assignmentNotFound(
            StaffBranchAssignmentNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "STAFF_BRANCH_ASSIGNMENT_NOT_FOUND",
                "Staff branch assignment not found",
                "The staff branch assignment was not found.");
    }

    @ExceptionHandler(StaffScopeNotFoundException.class)
    ResponseEntity<ProblemDetail> scopeNotFound(StaffScopeNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "STAFF_SCOPE_NOT_FOUND",
                "Staff scope not found",
                "The staff scope was not found.");
    }

    @ExceptionHandler({
            StaffBranchAssignmentDuplicateException.class,
            StaffBranchAssignmentStateConflictException.class})
    ResponseEntity<ProblemDetail> assignmentConflict(RuntimeException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "STAFF_BRANCH_ASSIGNMENT_CONFLICT",
                "Staff branch assignment conflict",
                "The staff branch assignment cannot be changed in its current state.");
    }

    @ExceptionHandler(StaffScopeStateConflictException.class)
    ResponseEntity<ProblemDetail> scopeConflict(StaffScopeStateConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "STAFF_SCOPE_CONFLICT",
                "Staff scope conflict",
                "The staff scope cannot be changed in its current state.");
    }

    @ExceptionHandler(StaffBranchAssignmentVersionConflictException.class)
    ResponseEntity<ProblemDetail> assignmentVersionConflict(
            StaffBranchAssignmentVersionConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "STAFF_BRANCH_ASSIGNMENT_VERSION_CONFLICT",
                "Staff assignment version conflict",
                "The staff branch assignment was modified; reload it and retry.");
    }

    @ExceptionHandler(StaffScopeVersionConflictException.class)
    ResponseEntity<ProblemDetail> scopeVersionConflict(
            StaffScopeVersionConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "STAFF_SCOPE_VERSION_CONFLICT",
                "Staff scope version conflict",
                "The staff scope was modified; reload it and retry.");
    }

    @ExceptionHandler(ActiveBranchContextUnavailableException.class)
    ResponseEntity<ProblemDetail> activeBranchUnavailable(
            ActiveBranchContextUnavailableException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "ACTIVE_BRANCH_UNAVAILABLE",
                "Active branch unavailable",
                "The active branch is no longer available; select an active branch again.");
    }

    @ExceptionHandler({
            StaffBranchAssignmentDataAccessException.class,
            StaffScopeDataAccessException.class})
    ResponseEntity<ProblemDetail> dataAccess(RuntimeException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "STAFF_BRANCH_DATA_ACCESS_FAILED",
                "Staff branch operation failed",
                "The staff branch operation could not be completed.");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String title,
            String detail) {
        ProblemDetail problem = ApiProblemFactory.create(status, code, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }
}
