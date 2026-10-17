package io.github.guillermodubon.coachgym.auth.web;

import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import io.github.guillermodubon.coachgym.user.ActiveBranchContextUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Stable, privacy-safe errors for authenticated-session context resolution. */
@RestControllerAdvice(assignableTypes = AuthenticationController.class)
class AuthenticationProblemHandler {

    @ExceptionHandler(ActiveBranchContextUnavailableException.class)
    ProblemDetail activeBranchUnavailable(ActiveBranchContextUnavailableException exception) {
        ProblemDetail problem = ApiProblemFactory.create(
                HttpStatus.CONFLICT,
                "ACTIVE_BRANCH_UNAVAILABLE",
                "The active branch context is no longer available; select an active branch again.");
        problem.setTitle("Active branch unavailable");
        return problem;
    }
}
