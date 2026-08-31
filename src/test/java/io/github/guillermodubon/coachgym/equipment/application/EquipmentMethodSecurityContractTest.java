package io.github.guillermodubon.coachgym.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class EquipmentMethodSecurityContractTest {

    private static final String ADMIN =
            "hasRole('ADMIN')";

    private static final String READ_ROLES =
            "hasAnyRole('ADMIN', 'MAINTENANCE', 'RECEPTIONIST')";

    private static final String OPERATIONAL_ROLES =
            "hasAnyRole('ADMIN', 'MAINTENANCE')";

    @Test
    void equipmentServiceUsesApprovedSecurityMatrix() {
        assertPreAuthorize(
                EquipmentApplicationService.class,
                "register",
                ADMIN);

        assertPreAuthorize(
                EquipmentApplicationService.class,
                "update",
                ADMIN);

        assertPreAuthorize(
                EquipmentApplicationService.class,
                "markOutOfService",
                OPERATIONAL_ROLES);

        assertPreAuthorize(
                EquipmentApplicationService.class,
                "markAvailable",
                OPERATIONAL_ROLES);

        assertPreAuthorize(
                EquipmentApplicationService.class,
                "retire",
                ADMIN);

        assertPreAuthorize(
                EquipmentApplicationService.class,
                "findById",
                READ_ROLES);

        assertPreAuthorize(
                EquipmentApplicationService.class,
                "findAll",
                READ_ROLES);
    }

    @Test
    void categoryServiceUsesApprovedSecurityMatrix() {
        assertPreAuthorize(
                EquipmentCategoryApplicationService.class,
                "create",
                ADMIN);

        assertPreAuthorize(
                EquipmentCategoryApplicationService.class,
                "update",
                ADMIN);

        assertPreAuthorize(
                EquipmentCategoryApplicationService.class,
                "activate",
                ADMIN);

        assertPreAuthorize(
                EquipmentCategoryApplicationService.class,
                "deactivate",
                ADMIN);

        assertPreAuthorize(
                EquipmentCategoryApplicationService.class,
                "findById",
                READ_ROLES);

        assertPreAuthorize(
                EquipmentCategoryApplicationService.class,
                "findAll",
                READ_ROLES);
    }

    private static void assertPreAuthorize(
            Class<?> serviceType,
            String methodName,
            String expectedExpression) {

        Method method =
                Arrays.stream(
                                serviceType.getDeclaredMethods())
                        .filter(candidate ->
                                candidate.getName()
                                        .equals(methodName))
                        .findFirst()
                        .orElseThrow(() ->
                                new AssertionError(
                                        "Method not found: "
                                                + serviceType.getName()
                                                + "."
                                                + methodName));

        PreAuthorize annotation =
                method.getAnnotation(
                        PreAuthorize.class);

        assertThat(annotation)
                .as(
                        "%s.%s must declare @PreAuthorize",
                        serviceType.getSimpleName(),
                        methodName)
                .isNotNull();

        assertThat(annotation.value())
                .isEqualTo(expectedExpression);
    }
}
