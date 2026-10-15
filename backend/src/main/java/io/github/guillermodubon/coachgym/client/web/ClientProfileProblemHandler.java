package io.github.guillermodubon.coachgym.client.web;

import io.github.guillermodubon.coachgym.client.application.ClientNotFoundException;
import io.github.guillermodubon.coachgym.client.application.ClientPhotoNotFoundException;
import io.github.guillermodubon.coachgym.client.application.ClientPhotoStorageException;
import io.github.guillermodubon.coachgym.client.application.ClientPhotoTooLargeException;
import io.github.guillermodubon.coachgym.client.application.ClientPhotoValidationException;
import io.github.guillermodubon.coachgym.client.application.ClientProfileDataAccessException;
import io.github.guillermodubon.coachgym.client.application.ClientStateConflictException;
import io.github.guillermodubon.coachgym.client.application.ClientValidationException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        assignableTypes = {
                ClientProfileController.class,
                ClientPhotoController.class
        })
class ClientProfileProblemHandler {

    @ExceptionHandler(ClientNotFoundException.class)
    ProblemDetail handleClientNotFound(
            ClientNotFoundException exception) {

        return problem(
                HttpStatus.NOT_FOUND,
                "CLIENT_NOT_FOUND",
                "Client not found",
                "The requested client was not found.");
    }

    @ExceptionHandler(ClientPhotoNotFoundException.class)
    ProblemDetail handleClientPhotoNotFound(
            ClientPhotoNotFoundException exception) {

        return problem(
                HttpStatus.NOT_FOUND,
                "CLIENT_PHOTO_NOT_FOUND",
                "Client photo not found",
                "The requested client photo was not found.");
    }

    @ExceptionHandler(ClientPhotoTooLargeException.class)
    ProblemDetail handleClientPhotoTooLarge(
            ClientPhotoTooLargeException exception) {

        return problem(
                HttpStatus.CONTENT_TOO_LARGE,
                "CLIENT_PHOTO_TOO_LARGE",
                "Client photo is too large",
                exception.getMessage());
    }

    @ExceptionHandler({
            ClientValidationException.class,
            ClientPhotoValidationException.class,
            IllegalArgumentException.class
    })
    ProblemDetail handleClientValidation(
            RuntimeException exception) {

        return problem(
                HttpStatus.BAD_REQUEST,
                "CLIENT_VALIDATION_FAILED",
                "Client validation failed",
                safeMessage(
                        exception,
                        "The client request is invalid."));
    }

    @ExceptionHandler(ClientStateConflictException.class)
    ProblemDetail handleClientStateConflict(
            ClientStateConflictException exception) {

        return problem(
                HttpStatus.CONFLICT,
                "CLIENT_STATE_CONFLICT",
                "Client state conflict",
                "The client was modified or is not in the required state.");
    }

    @ExceptionHandler({
            ClientPhotoStorageException.class,
            ClientProfileDataAccessException.class
    })
    ProblemDetail handleClientInfrastructureFailure(
            RuntimeException exception) {

        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "CLIENT_OPERATION_FAILED",
                "Client operation failed",
                "The client operation could not be completed.");
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String code,
            String title,
            String detail) {

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        status,
                        detail);

        problem.setTitle(title);
        problem.setType(
                URI.create(
                        "urn:coachgym:problem:"
                                + code.toLowerCase(
                                java.util.Locale.ROOT)));
        problem.setProperty(
                "code",
                code);

        return problem;
    }

    private static String safeMessage(
            RuntimeException exception,
            String fallback) {

        if (exception.getMessage() == null
                || exception.getMessage().isBlank()) {

            return fallback;
        }

        return exception.getMessage();
    }
}
