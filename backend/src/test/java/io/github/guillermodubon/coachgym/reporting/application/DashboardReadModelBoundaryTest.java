package io.github.guillermodubon.coachgym.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class DashboardReadModelBoundaryTest {

    private static final List<Class<?>> PORTS = List.of(
            MembershipDashboardQuery.class,
            AccessDashboardQuery.class,
            PaymentDashboardQuery.class,
            EquipmentDashboardQuery.class,
            IncidentDashboardQuery.class,
            MaintenanceDashboardQuery.class,
            DashboardNotificationQuery.class,
            DashboardSettingsQuery.class);

    @Test
    void portsExposeOnlyReportingAndJdkTypes() {
        for (Class<?> port : PORTS) {
            for (Method method : port.getDeclaredMethods()) {
                assertAllowed(method.getReturnType());
                for (Class<?> parameterType : method.getParameterTypes()) {
                    assertAllowed(parameterType);
                }
            }
        }
    }

    @Test
    void portsDeclareNoMutationLanguage() {
        for (Class<?> port : PORTS) {
            assertThat(port.getDeclaredMethods())
                    .allSatisfy(method -> {
                        String methodName = method.getName();

                        assertThat(methodName).doesNotStartWith("create");
                        assertThat(methodName).doesNotStartWith("save");
                        assertThat(methodName).doesNotStartWith("update");
                        assertThat(methodName).doesNotStartWith("delete");
                        assertThat(methodName).doesNotStartWith("mark");
                        assertThat(methodName).doesNotStartWith("change");
                    });
        }
    }

    @Test
    void contractsArePublicInterfaces() {
        assertThat(PORTS).allSatisfy(port -> {
            assertThat(port.isInterface()).isTrue();
            assertThat(Modifier.isPublic(port.getModifiers())).isTrue();
        });
    }

    private static void assertAllowed(Class<?> type) {
        if (type.isPrimitive()) {
            return;
        }
        String packageName = type.getPackageName();
        assertThat(packageName)
                .matches(
                        "java(\\..*)?"
                                + "|io\\.github\\.guillermodubon\\.coachgym"
                                + "\\.reporting(\\..*)?");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(String... authorities) {
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(
                        "dashboard-user",
                        null,
                        authorities);

        authentication.setAuthenticated(true);

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

}
