package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.application.MembershipPeriodCoverageSummaryUnavailableException;
import io.github.guillermodubon.coachgym.membership.application.MembershipPeriodNotFoundException;
import io.github.guillermodubon.coachgym.membership.application.MembershipPeriodQueryUnavailableException;
import io.github.guillermodubon.coachgym.membership.application.MembershipNotFoundException;
import io.github.guillermodubon.coachgym.membership.application.MembershipQueryUnavailableException;
import io.github.guillermodubon.coachgym.membership.application.MembershipQueryValidationException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Stable, safe ProblemDetail responses for membership search. */
@RestControllerAdvice(assignableTypes = {
        MembershipQueryController.class,
        MembershipPeriodQueryController.class})
class MembershipQueryProblemHandler {

    @ExceptionHandler(MembershipQueryValidationException.class)
    ResponseEntity<ProblemDetail> invalidQuery(MembershipQueryValidationException exception) {
        return ResponseEntity.badRequest().body(ApiProblemFactory.create(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_PARAMETER",
                exception.getMessage()));
    }

    @ExceptionHandler(MembershipQueryUnavailableException.class)
    ResponseEntity<ProblemDetail> queryUnavailable(MembershipQueryUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiProblemFactory.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "MEMBERSHIP_QUERY_UNAVAILABLE",
                "The membership page could not be returned."));
    }

    @ExceptionHandler(MembershipPeriodCoverageSummaryUnavailableException.class)
    ResponseEntity<ProblemDetail> coverageUnavailable(
            MembershipPeriodCoverageSummaryUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiProblemFactory.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "MEMBERSHIP_COVERAGE_SUMMARY_UNAVAILABLE",
                "The historical coverage summary could not be returned."));
    }

    @ExceptionHandler(MembershipNotFoundException.class)
    ResponseEntity<ProblemDetail> membershipNotFound(MembershipNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiProblemFactory.create(
                HttpStatus.NOT_FOUND,
                "MEMBERSHIP_NOT_FOUND",
                "The requested membership was not found."));
    }

    @ExceptionHandler(MembershipPeriodNotFoundException.class)
    ResponseEntity<ProblemDetail> periodNotFound(MembershipPeriodNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiProblemFactory.create(
                HttpStatus.NOT_FOUND,
                "MEMBERSHIP_PERIOD_NOT_FOUND",
                "The requested membership period was not found."));
    }

    @ExceptionHandler(MembershipPeriodQueryUnavailableException.class)
    ResponseEntity<ProblemDetail> periodQueryUnavailable(
            MembershipPeriodQueryUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiProblemFactory.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "MEMBERSHIP_PERIOD_QUERY_UNAVAILABLE",
                "The membership period query could not be returned."));
    }
}
