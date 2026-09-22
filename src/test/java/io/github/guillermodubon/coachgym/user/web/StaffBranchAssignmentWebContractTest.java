package io.github.guillermodubon.coachgym.user.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class StaffBranchAssignmentWebContractTest {

    @Test
    void exposesAdministrativeLifecycleAndScopeRoutes() {
        assertThat(StaffBranchAssignmentController.class
                .getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/staff");
        assertMapping("findAssignments", GetMapping.class, "/branch-assignments");
        assertMapping("findUserAssignments", GetMapping.class, "/{userId}/branch-assignments");
        assertMapping("assign", PostMapping.class, "/branch-assignments");
        assertMapping("end", PostMapping.class, "/branch-assignments/{assignmentId}/end");
        assertMapping("findScope", GetMapping.class, "/{userId}/scope");
        assertMapping("changeScope", PutMapping.class, "/{userId}/scope");
    }

    @Test
    void exposesAuthenticatedUserBranchContextRoutes() {
        assertThat(StaffBranchContextController.class
                .getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/v1/me/branch-context");
        assertMapping(StaffBranchContextController.class, "get", GetMapping.class, "");
        assertMapping(StaffBranchContextController.class, "select", PutMapping.class, "");
        assertMapping(StaffBranchContextController.class, "clear", DeleteMapping.class, "");
    }

    @Test
    void requestContractsDoNotContainRoleOrStatusMutationFields() {
        assertThat(Arrays.stream(StaffBranchAssignmentRequest.class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactlyInAnyOrder("targetUserId", "branchId", "reason")
                .doesNotContain("role", "status", "permissions");
        assertThat(Arrays.stream(ChangeStaffScopeRequest.class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactlyInAnyOrder("requestedScope", "reason", "expectedVersion")
                .doesNotContain("role", "status", "permissions");
    }

    private static void assertMapping(
            String methodName,
            Class<? extends java.lang.annotation.Annotation> annotationType,
            String expectedPath) {
        assertMapping(StaffBranchAssignmentController.class, methodName, annotationType, expectedPath);
    }

    private static void assertMapping(
            Class<?> controller,
            String methodName,
            Class<? extends java.lang.annotation.Annotation> annotationType,
            String expectedPath) {
        Method method = Arrays.stream(controller.getDeclaredMethods())
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
