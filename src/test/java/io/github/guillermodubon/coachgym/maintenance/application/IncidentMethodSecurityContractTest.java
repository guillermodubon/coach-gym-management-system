package io.github.guillermodubon.coachgym.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class IncidentMethodSecurityContractTest {

    @Test
    void incidentServiceUsesApprovedSecurityMatrix() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("report", "hasAnyRole('ADMIN', 'RECEPTIONIST')");
        expected.put("startInvestigation", "hasRole('ADMIN')");
        expected.put("changePriority", "hasRole('ADMIN')");
        expected.put("resolve", "hasRole('ADMIN')");
        expected.put("findById", "hasAnyRole('ADMIN', 'RECEPTIONIST')");
        expected.put("findAll", "hasAnyRole('ADMIN', 'RECEPTIONIST')");
        expected.put("findStatusHistory", "hasAnyRole('ADMIN', 'RECEPTIONIST')");

        expected.forEach((methodName, expression) ->
                assertPreAuthorize(methodName, expression));
    }

    private static void assertPreAuthorize(
            String methodName,
            String expectedExpression) {
        Method[] methods = Arrays.stream(
                        IncidentApplicationService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .toArray(Method[]::new);

        assertThat(methods)
                .as("Expected one method named %s", methodName)
                .hasSize(1);

        PreAuthorize annotation = methods[0].getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as("%s must declare @PreAuthorize", methodName)
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(expectedExpression);
    }
}
