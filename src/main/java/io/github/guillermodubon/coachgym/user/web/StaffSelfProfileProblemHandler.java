package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import io.github.guillermodubon.coachgym.user.application.StaffCurrentPasswordInvalidException;
import io.github.guillermodubon.coachgym.user.application.StaffProfileDataAccessException;
import io.github.guillermodubon.coachgym.user.application.StaffProfileNotFoundException;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoNotFoundException;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoStorageException;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoTooLargeException;
import io.github.guillermodubon.coachgym.user.application.StaffProfileValidationException;
import io.github.guillermodubon.coachgym.user.application.StaffProfileVersionConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/** Stable, privacy-safe ProblemDetail mapping for self-profile operations. */
@RestControllerAdvice(assignableTypes = StaffSelfProfileController.class)
class StaffSelfProfileProblemHandler {

    @ExceptionHandler(StaffProfileNotFoundException.class)
    ProblemDetail profileNotFound(StaffProfileNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "STAFF_PROFILE_NOT_FOUND",
                "Staff profile not found",
                "The authenticated staff profile was not found.");
    }

    @ExceptionHandler(StaffProfilePhotoNotFoundException.class)
    ProblemDetail photoNotFound(StaffProfilePhotoNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "STAFF_PROFILE_PHOTO_NOT_FOUND",
                "Staff profile photo not found",
                "The authenticated staff profile photo was not found.");
    }

    @ExceptionHandler(StaffProfileVersionConflictException.class)
    ProblemDetail versionConflict(StaffProfileVersionConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "STAFF_PROFILE_VERSION_CONFLICT",
                "Staff profile version conflict",
                "The staff profile was modified; reload it and retry.");
    }

    @ExceptionHandler(StaffCurrentPasswordInvalidException.class)
    ProblemDetail currentPasswordInvalid(StaffCurrentPasswordInvalidException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "CURRENT_PASSWORD_INVALID",
                "Current password invalid",
                "The current password could not be verified.");
    }

    @ExceptionHandler({
            StaffProfileValidationException.class,
            IllegalArgumentException.class,
            MultipartException.class,
            MethodArgumentNotValidException.class,
            MissingServletRequestPartException.class
    })
    ProblemDetail validation(Exception exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "STAFF_PROFILE_VALIDATION_FAILED",
                "Staff profile validation failed",
                "The staff profile request is invalid.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail photoTooLarge(MaxUploadSizeExceededException exception) {
        return problem(
                HttpStatus.CONTENT_TOO_LARGE,
                "STAFF_PROFILE_PHOTO_TOO_LARGE",
                "Staff profile photo is too large",
                "The staff profile photo exceeds the allowed size.");
    }

    @ExceptionHandler(StaffProfilePhotoTooLargeException.class)
    ProblemDetail photoTooLarge(StaffProfilePhotoTooLargeException exception) {
        return problem(
                HttpStatus.CONTENT_TOO_LARGE,
                "STAFF_PROFILE_PHOTO_TOO_LARGE",
                "Staff profile photo is too large",
                "The staff profile photo exceeds the allowed size.");
    }

    @ExceptionHandler({
            StaffProfilePhotoStorageException.class
    })
    ProblemDetail storageFailure(RuntimeException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "STAFF_PROFILE_STORAGE_FAILED",
                "Staff profile storage failed",
                "The staff profile photo operation could not be completed.");
    }

    @ExceptionHandler(StaffProfileDataAccessException.class)
    ProblemDetail dataAccessFailure(StaffProfileDataAccessException exception) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "STAFF_PROFILE_DATA_ACCESS_FAILED",
                "Staff profile operation failed",
                "The staff profile operation could not be completed.");
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String code,
            String title,
            String detail) {
        ProblemDetail problem = ApiProblemFactory.create(status, code, detail);
        problem.setTitle(title);
        return problem;
    }
}
