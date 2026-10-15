package io.github.guillermodubon.coachgym.user.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.user.application.StaffSelfProfileApplicationService;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class StaffSelfProfileWebContractTest {

    @Test
    void exposesOnlyActorDerivedSelfProfileRoutes() {
        RequestMapping root = StaffSelfProfileController.class
                .getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/api/v1/me/profile");

        assertMapping("findProfile", GetMapping.class, "");
        assertMapping("update", PutMapping.class, "");
        assertMapping("uploadPhoto", PutMapping.class, "/photo");
        assertMapping("downloadPhoto", GetMapping.class, "/photo");
        assertMapping("removePhoto", DeleteMapping.class, "/photo");
        assertMapping("changePassword", PostMapping.class, "/password");

        assertThat(Arrays.stream(StaffSelfProfileController.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameters()))
                .map(parameter -> parameter.getType().getName()))
                .doesNotContain("java.util.UUID");
    }

    @Test
    void protectsMutatingServiceOperationsWithSupportedStaffRoles() {
        assertThat(PreAuthorize.class).isNotNull();
        assertThat(Arrays.stream(StaffSelfProfileApplicationService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("update")
                        || method.getName().equals("uploadPhoto")
                        || method.getName().equals("removePhoto")
                        || method.getName().equals("changePassword"))
                .map(method -> method.getAnnotation(PreAuthorize.class))
                .filter(java.util.Objects::nonNull)
                .map(PreAuthorize::value)
                .allMatch(value -> value.contains("ADMIN") && value.contains("RECEPTIONIST")))
                .isTrue();
    }

    @Test
    void downloadReturnsBinaryResponseAndNotAStorageProjection() throws Exception {
        Method method = StaffSelfProfileController.class
                .getDeclaredMethod("downloadPhoto", org.springframework.security.core.Authentication.class);
        assertThat(method.getReturnType()).isEqualTo(ResponseEntity.class);
        assertThat(method.getAnnotation(GetMapping.class).produces())
                .contains(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp");
    }

    private static void assertMapping(
            String methodName,
            Class<? extends java.lang.annotation.Annotation> annotationType,
            String expectedPath) {
        Method method = Arrays.stream(StaffSelfProfileController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        java.lang.annotation.Annotation annotation = method.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        String[] paths = switch (annotation) {
            case GetMapping mapping -> mapping.value();
            case PutMapping mapping -> mapping.value();
            case DeleteMapping mapping -> mapping.value();
            case PostMapping mapping -> mapping.value();
            default -> throw new AssertionError("Unsupported mapping annotation");
        };
        if (expectedPath.isEmpty()) {
            assertThat(paths).isEmpty();
        } else {
            assertThat(paths).containsExactly(expectedPath);
        }
    }
}
