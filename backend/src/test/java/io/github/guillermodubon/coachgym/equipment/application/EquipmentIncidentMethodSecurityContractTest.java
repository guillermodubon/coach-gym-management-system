package io.github.guillermodubon.coachgym.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class EquipmentIncidentMethodSecurityContractTest {

    @Test
    void incidentWithdrawalUsesTheApprovedRoleMatrix()
            throws NoSuchMethodException {

        Method method = EquipmentIncidentApplicationService.class
                .getDeclaredMethod(
                        "takeOutOfServiceForIncident",
                        UUID.class,
                        String.class,
                        long.class,
                        io.github.guillermodubon.coachgym.user.AuthenticatedActor.class,
                        Instant.class);

        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value())
                .isEqualTo("hasAnyRole('ADMIN', 'RECEPTIONIST')");
    }
}
