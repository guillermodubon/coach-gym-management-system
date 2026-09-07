package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.reporting.application.DashboardCurrencyConflictException;
import io.github.guillermodubon.coachgym.reporting.application.DashboardDataAccessException;
import io.github.guillermodubon.coachgym.reporting.application.ReportingValidationException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DashboardController.class)
class DashboardProblemHandler {

    @ExceptionHandler(ReportingValidationException.class)
    ProblemDetail validation(ReportingValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "REPORTING_VALIDATION_FAILED",
                "Invalid reporting request", exception.getMessage());
    }

    @ExceptionHandler(DashboardCurrencyConflictException.class)
    ProblemDetail currency(DashboardCurrencyConflictException exception) {
        ProblemDetail detail = problem(HttpStatus.CONFLICT,
                "REPORTING_CURRENCY_CONFLICT", "Reporting currency conflict",
                exception.getMessage());
        detail.setProperty("currencies", exception.currencies());
        return detail;
    }

    @ExceptionHandler(DashboardDataAccessException.class)
    ProblemDetail dataAccess(DashboardDataAccessException exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "REPORTING_DATA_ACCESS_FAILED", "Reporting data unavailable",
                "Operational dashboard data could not be read.");
    }

    private static ProblemDetail problem(
            HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:coachgym:problem:" + code.toLowerCase()));
        problem.setProperty("code", code);
        return problem;
    }
}
