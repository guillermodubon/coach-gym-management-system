package io.github.guillermodubon.coachgym.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guards the read-model boundary of the reporting module. */
class ReportingArchitectureContractTest {

    private static final List<String> FORBIDDEN_TYPE_FRAGMENTS = List.of(
            ".domain.",
            ".infrastructure.persistence." + "MembershipJpa",
            ".infrastructure.persistence." + "PaymentJpa",
            ".infrastructure.persistence." + "AccessRecordJpa",
            ".infrastructure.persistence." + "EquipmentJpa",
            ".infrastructure.persistence." + "IncidentJpa",
            ".infrastructure.persistence." + "MaintenanceJpa",
            "JpaRepository");

    @Test
    void dashboardApplicationServiceDependsOnlyOnPortsAndReportingComponents() {
        for (Field field : io.github.guillermodubon.coachgym.reporting.application
                .DashboardApplicationService.class.getDeclaredFields()) {
            String typeName = field.getType().getName();

            assertThat(FORBIDDEN_TYPE_FRAGMENTS)
                    .noneMatch(typeName::contains);
        }
    }

    @Test
    void reportingPublicModelsRemainImmutableRecords() {
        assertThat(List.of(
                OperationalDashboardDetails.class,
                DashboardPeriodDetails.class,
                MembershipDashboardDetails.class,
                AccessDashboardDetails.class,
                PaymentDashboardDetails.class,
                EquipmentDashboardDetails.class,
                IncidentDashboardDetails.class,
                MaintenanceDashboardDetails.class,
                DashboardNotificationDetails.class))
                .allSatisfy(type -> assertThat(type.isRecord()).isTrue());
    }
}
