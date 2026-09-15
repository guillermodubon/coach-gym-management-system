package io.github.guillermodubon.coachgym.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class MaintenanceMethodSecurityContractTest {

    @Test
    void mutationsRequireAdminAndQueriesAllowReceptionist() {
        Map<String, String> expected = Map.of(
                "schedule", "hasRole('ADMIN')",
                "updateScheduled", "hasRole('ADMIN')",
                "start", "hasRole('ADMIN')",
                "complete", "hasRole('ADMIN')",
                "cancel", "hasRole('ADMIN')",
                "findById", "hasAnyRole('ADMIN', 'RECEPTIONIST')",
                "findAll", "hasAnyRole('ADMIN', 'RECEPTIONIST')",
                "findStatusHistory", "hasAnyRole('ADMIN', 'RECEPTIONIST')");

        expected.forEach(MaintenanceMethodSecurityContractTest::assertExpression);
    }

    private static void assertExpression(String methodName, String expression) {
        Method method = Arrays.stream(
                        MaintenanceApplicationService.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(expression);
    }
}
