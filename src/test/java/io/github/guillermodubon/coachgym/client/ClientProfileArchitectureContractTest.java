package io.github.guillermodubon.coachgym.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.client.application.ClientOperationalProfileQuery;
import io.github.guillermodubon.coachgym.client.application.ClientPhotoStorage;
import io.github.guillermodubon.coachgym.client.application.ClientPhotoStore;
import io.github.guillermodubon.coachgym.client.application.ClientSearchStore;
import io.github.guillermodubon.coachgym.client.application.ClientStatusHistoryQuery;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Final boundary checks for the client search and operational profile feature. */
class ClientProfileArchitectureContractTest {

    private static final List<Class<?>> READ_PORTS = List.of(
            ClientSearchStore.class,
            ClientOperationalProfileQuery.class,
            ClientStatusHistoryQuery.class,
            ClientPhotoStore.class,
            ClientPhotoStorage.class);

    @Test
    void clientProfilePortsRemainPublicAbstractions() {
        assertThat(READ_PORTS).allSatisfy(port -> {
            assertThat(port.isInterface())
                    .as("%s must remain an interface", port.getName())
                    .isTrue();
            assertThat(Modifier.isPublic(port.getModifiers()))
                    .as("%s must remain public", port.getName())
                    .isTrue();
        });
    }

    @Test
    void clientProfilePortsDoNotExposeWebPersistenceOrSecurityTypes() {
        for (Class<?> port : READ_PORTS) {
            for (Method method : port.getDeclaredMethods()) {
                assertAllowedType(method.getReturnType());
                for (Class<?> parameterType : method.getParameterTypes()) {
                    assertAllowedType(parameterType);
                }
            }
        }
    }

    @Test
    void publicClientProfileModelsRemainImmutableRecords() {
        assertThat(List.of(
                ClientSummary.class,
                ClientPage.class,
                ClientOperationalProfile.class,
                ClientEmergencyContactDetails.class,
                ClientMembershipSummary.class,
                ClientPaymentSummary.class,
                ClientAccessSummary.class,
                ClientPhotoDetails.class,
                ClientStatusHistoryDetails.class))
                .allSatisfy(type -> assertThat(type.isRecord())
                        .as("%s must remain an immutable record", type.getName())
                        .isTrue());
    }

    private static void assertAllowedType(Class<?> type) {
        if (type.isPrimitive() || type.isArray()) {
            return;
        }

        String packageName = type.getPackageName();
        boolean allowed = packageName.equals("java")
                || packageName.startsWith("java.")
                || packageName.equals(
                        "io.github.guillermodubon.coachgym.client")
                || packageName.startsWith(
                        "io.github.guillermodubon.coachgym.client.")
                || packageName.equals(
                        "io.github.guillermodubon.coachgym.user");

        assertThat(allowed)
                .as("Unexpected type exposed by client profile port: %s", type.getName())
                .isTrue();
    }
}
