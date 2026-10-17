package io.github.guillermodubon.coachgym.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.reporting.AccessDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.DashboardNotificationDetails;
import io.github.guillermodubon.coachgym.reporting.EquipmentDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.IncidentDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.MaintenanceDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.MembershipDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.PaymentDashboardDetails;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardQueryContractTest {

    @Test
    void exposesExpectedReadPortSignatures() throws Exception {
        assertMethod(
                MembershipDashboardQuery.class,
                "summarize",
                MembershipDashboardDetails.class,
                DashboardPeriod.class,
                int.class);
        assertMethod(
                AccessDashboardQuery.class,
                "summarizeToday",
                AccessDashboardDetails.class,
                DashboardPeriod.class);
        assertMethod(
                PaymentDashboardQuery.class,
                "summarize",
                PaymentDashboardDetails.class,
                DashboardPeriod.class,
                String.class);
        assertMethod(
                EquipmentDashboardQuery.class,
                "summarize",
                EquipmentDashboardDetails.class);
        assertMethod(
                IncidentDashboardQuery.class,
                "summarize",
                IncidentDashboardDetails.class);
        assertMethod(
                MaintenanceDashboardQuery.class,
                "summarize",
                MaintenanceDashboardDetails.class,
                LocalDate.class);
        assertMethod(
                DashboardNotificationQuery.class,
                "summarize",
                DashboardNotificationDetails.class,
                UUID.class);
        assertMethod(
                DashboardSettingsQuery.class,
                "load",
                DashboardSettings.class);
    }

    private static void assertMethod(
            Class<?> contract,
            String name,
            Class<?> returnType,
            Class<?>... parameterTypes) throws Exception {
        assertThat(contract.isInterface()).isTrue();
        Method method = contract.getMethod(name, parameterTypes);
        assertThat(method.getReturnType()).isEqualTo(returnType);
    }
}
