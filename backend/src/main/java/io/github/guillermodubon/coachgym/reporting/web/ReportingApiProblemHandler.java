package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.reporting.application.ReportingAccessDeniedException;
import io.github.guillermodubon.coachgym.reporting.application.ReportingDataAccessException;
import io.github.guillermodubon.coachgym.reporting.application.ReportingValidationException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ReportingApiController.class)
class ReportingApiProblemHandler {

    @ExceptionHandler(ReportingValidationException.class)
    ProblemDetail invalidRequest() {
        return ApiProblemFactory.create(
                HttpStatus.BAD_REQUEST,
                "REPORTING_VALIDATION_FAILED",
                "The reporting request is invalid.");
    }

    @ExceptionHandler(ReportingAccessDeniedException.class)
    ProblemDetail accessDenied() {
        return ApiProblemFactory.create(
                HttpStatus.FORBIDDEN,
                "REPORTING_ACCESS_DENIED",
                "Reporting data is not available for the requested actor or scope.");
    }

    @ExceptionHandler(ReportingDataAccessException.class)
    ProblemDetail dataUnavailable() {
        return ApiProblemFactory.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "REPORTING_DATA_ACCESS_FAILED",
                "Reporting data could not be read.");
    }
}
