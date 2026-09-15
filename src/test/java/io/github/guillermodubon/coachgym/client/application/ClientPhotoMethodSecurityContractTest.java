package io.github.guillermodubon.coachgym.client.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class ClientPhotoMethodSecurityContractTest {

    @Test
    void uploadAndReadAllowCurrentRolesWhileDeletionIsAdminOnly() throws Exception {
        assertExpression("upload", new Class<?>[]{UUID.class, UploadClientPhotoCommand.class,
                        io.github.guillermodubon.coachgym.user.AuthenticatedActor.class},
                "hasAnyRole('ADMIN', 'RECEPTIONIST')");
        assertExpression("load", new Class<?>[]{UUID.class},
                "hasAnyRole('ADMIN', 'RECEPTIONIST')");
        assertExpression("delete", new Class<?>[]{UUID.class, long.class},
                "hasRole('ADMIN')");
    }

    private static void assertExpression(
            String name, Class<?>[] parameterTypes, String expected) throws Exception {
        Method method = ClientPhotoApplicationService.class.getMethod(name, parameterTypes);
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo(expected);
    }
}
