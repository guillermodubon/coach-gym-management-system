package io.github.guillermodubon.coachgym.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class EquipmentMethodSecurityContractTest {

    @Test
    void equipmentServiceUsesApprovedSecurityMatrix() {
        Map<String, String> expectedExpressions = new LinkedHashMap<>();

        expectedExpressions.put(
                "register",
                "hasRole('ADMIN')");
        expectedExpressions.put(
                "update",
                "hasRole('ADMIN')");
        expectedExpressions.put(
                "markOutOfService",
                "hasRole('ADMIN')");
        expectedExpressions.put(
                "markAvailable",
                "hasRole('ADMIN')");
        expectedExpressions.put(
                "retire",
                "hasRole('ADMIN')");
        expectedExpressions.put(
                "findById",
                "hasAnyRole('ADMIN', 'RECEPTIONIST')");
        expectedExpressions.put(
                "findAll",
                "hasAnyRole('ADMIN', 'RECEPTIONIST')");

        expectedExpressions.forEach(
                (methodName, expression) ->
                        assertPreAuthorize(
                                EquipmentApplicationService.class,
                                methodName,
                                expression));
    }

    @Test
    void categoryServiceUsesApprovedSecurityMatrix() {
        Map<String, String> expectedExpressions = new LinkedHashMap<>();

        expectedExpressions.put(
                "create",
                "hasRole('ADMIN')");
        expectedExpressions.put(
                "update",
                "hasRole('ADMIN')");
        expectedExpressions.put(
                "activate",
                "hasRole('ADMIN')");
        expectedExpressions.put(
                "deactivate",
                "hasRole('ADMIN')");
        expectedExpressions.put(
                "findById",
                "hasAnyRole('ADMIN', 'RECEPTIONIST')");
        expectedExpressions.put(
                "findAll",
                "hasAnyRole('ADMIN', 'RECEPTIONIST')");

        expectedExpressions.forEach(
                (methodName, expression) ->
                        assertPreAuthorize(
                                EquipmentCategoryApplicationService.class,
                                methodName,
                                expression));
    }

    private static void assertPreAuthorize(
            Class<?> serviceType,
            String methodName,
            String expectedExpression) {

        Method method = findUniqueMethod(
                serviceType,
                methodName);

        PreAuthorize annotation =
                method.getAnnotation(PreAuthorize.class);

        assertThat(annotation)
                .as(
                        "%s.%s must declare @PreAuthorize",
                        serviceType.getSimpleName(),
                        methodName)
                .isNotNull();

        assertThat(annotation.value())
                .as(
                        "Unexpected authorization expression on %s.%s",
                        serviceType.getSimpleName(),
                        methodName)
                .isEqualTo(expectedExpression);
    }

    private static Method findUniqueMethod(
            Class<?> serviceType,
            String methodName) {

        Method[] matchingMethods =
                Arrays.stream(serviceType.getDeclaredMethods())
                        .filter(method ->
                                method.getName().equals(methodName))
                        .toArray(Method[]::new);

        assertThat(matchingMethods)
                .as(
                        "Expected exactly one method named %s on %s",
                        methodName,
                        serviceType.getSimpleName())
                .hasSize(1);

        return matchingMethods[0];
    }
}