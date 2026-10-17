package io.github.guillermodubon.coachgym.user.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.user.application.StaffCurrentPasswordInvalidException;
import io.github.guillermodubon.coachgym.user.application.StaffProfileNotFoundException;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoTooLargeException;
import io.github.guillermodubon.coachgym.user.application.StaffProfileVersionConflictException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class StaffSelfProfileProblemContractTest {

    @Test
    void mapsProfileErrorsToStableSafeCodes() {
        StaffSelfProfileProblemHandler handler = new StaffSelfProfileProblemHandler();

        ProblemDetail notFound = handler.profileNotFound(
                new StaffProfileNotFoundException());
        ProblemDetail conflict = handler.versionConflict(
                new StaffProfileVersionConflictException(UUID.randomUUID()));
        ProblemDetail password = handler.currentPasswordInvalid(
                new StaffCurrentPasswordInvalidException());
        ProblemDetail tooLarge = handler.photoTooLarge(
                new StaffProfilePhotoTooLargeException());

        assertThat(notFound.getStatus()).isEqualTo(404);
        assertThat(notFound.getProperties()).containsEntry(
                "code", "STAFF_PROFILE_NOT_FOUND");
        assertThat(conflict.getStatus()).isEqualTo(409);
        assertThat(conflict.getProperties()).containsEntry(
                "code", "STAFF_PROFILE_VERSION_CONFLICT");
        assertThat(password.getStatus()).isEqualTo(400);
        assertThat(password.getProperties()).containsEntry(
                "code", "CURRENT_PASSWORD_INVALID");
        assertThat(tooLarge.getStatus()).isEqualTo(413);
        assertThat(tooLarge.getProperties()).containsEntry(
                "code", "STAFF_PROFILE_PHOTO_TOO_LARGE");
        assertThat(password.toString()).doesNotContain(
                "new-password-123", "hash", "storage");
    }

    @Test
    void handlerDoesNotDeclareCredentialOrStorageFields() {
        String fields = java.util.Arrays.stream(
                        StaffSelfProfileProblemHandler.class.getDeclaredFields())
                .map(field -> field.getName().toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(" "));
        assertThat(fields).doesNotContain(
                "password", "token", "storagekey", "path", "bytes");
    }
}
