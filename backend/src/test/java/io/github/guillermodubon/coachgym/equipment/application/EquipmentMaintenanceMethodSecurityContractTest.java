package io.github.guillermodubon.coachgym.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class EquipmentMaintenanceMethodSecurityContractTest {

    @Test
    void allMaintenanceEquipmentMutationsRequireAdmin() {
        assertAdmin("startMaintenance");
        assertAdmin("completeMaintenance");
        assertAdmin("cancelInProgressMaintenance");
    }

    private static void assertAdmin(String name) {
        Method method = Arrays.stream(
                        EquipmentMaintenanceApplicationService.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }
}
