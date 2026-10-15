package io.github.guillermodubon.coachgym.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.user.application.StaffProfileValidationException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StaffSelfProfileRequestTest {

    @Test
    void updateRequestContainsOnlyEditableFieldsAndVersion() {
        UpdateStaffSelfProfileRequest request = new UpdateStaffSelfProfileRequest(
                " Ana ", " Martinez ", 3L);

        assertThat(request.toCommand().firstName()).isEqualTo("Ana");
        assertThat(request.toCommand().lastName()).isEqualTo("Martinez");
        assertThat(componentNames(UpdateStaffSelfProfileRequest.class))
                .containsExactlyInAnyOrder("firstName", "lastName", "version");
        assertThat(componentNames(UpdateStaffSelfProfileRequest.class))
                .doesNotContain("userId", "role", "status", "organizationScope",
                        "branchAssignments", "permissions");
    }

    @Test
    void passwordRequestDoesNotExposeCredentialValuesInItsStringRepresentation() {
        ChangeStaffPasswordRequest request = new ChangeStaffPasswordRequest(
                "current-password", "new-password-123", "new-password-123");

        assertThat(request.toString()).doesNotContain(
                "current-password", "new-password-123");
        assertThatCode(() -> request.toCommand())
                .doesNotThrowAnyException();
    }

    @Test
    void passwordRequestRejectsBlankValuesAtTheApplicationBoundary() {
        assertThatThrownBy(() -> new ChangeStaffPasswordRequest(
                "", "", "").toCommand())
                .isInstanceOf(StaffProfileValidationException.class);
        assertThatThrownBy(() -> new ChangeStaffPasswordRequest(
                "current", "short", "different").toCommand())
                .isInstanceOf(StaffProfileValidationException.class);
    }

    private static Set<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
