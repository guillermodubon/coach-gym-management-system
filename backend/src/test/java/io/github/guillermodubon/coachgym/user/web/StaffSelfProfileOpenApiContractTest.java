package io.github.guillermodubon.coachgym.user.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class StaffSelfProfileOpenApiContractTest {

    @Test
    void documentsEverySelfProfileOperationBehindSessionSecurity() {
        assertThat(StaffSelfProfileController.class
                .getAnnotation(SecurityRequirement.class).name())
                .isEqualTo("sessionCookie");

        assertThat(Arrays.stream(StaffSelfProfileController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PutMapping.class)
                        || method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(
                                org.springframework.web.bind.annotation.DeleteMapping.class))
                .allMatch(method -> method.isAnnotationPresent(Operation.class)))
                .isTrue();
    }

    @Test
    void advertisesOnlyApprovedPhotoMediaAndNoFutureScopeFields() {
        GetMapping photo = Arrays.stream(StaffSelfProfileController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("downloadPhoto"))
                .findFirst()
                .orElseThrow()
                .getAnnotation(GetMapping.class);

        assertThat(photo.produces())
                .containsExactly(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp");
        assertThat(Arrays.stream(StaffSelfProfileResponse.class.getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("organizationId", "organizationScope", "branchId", "branchAssignments");
    }

    @Test
    void passwordOperationDoesNotExposeCredentialFieldsInResponse() {
        assertThat(Arrays.stream(StaffPasswordChangeResponse.class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactlyInAnyOrder(
                        "userId", "profileVersion", "reauthenticationRequired")
                .doesNotContain("password", "passwordHash", "sessionId", "token");
        Method method = Arrays.stream(StaffSelfProfileController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("changePassword"))
                .findFirst()
                .orElseThrow();
        assertThat(method.getAnnotation(Operation.class).description())
                .contains("reauthentication")
                .doesNotContain("example password");
    }
}
