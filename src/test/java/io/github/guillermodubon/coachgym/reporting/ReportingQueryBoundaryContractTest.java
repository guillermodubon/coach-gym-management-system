package io.github.guillermodubon.coachgym.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.access.AccessBranchReportingSummary;
import io.github.guillermodubon.coachgym.access.AccessDailyTrendPoint;
import io.github.guillermodubon.coachgym.access.AccessOperationalDaySummary;
import io.github.guillermodubon.coachgym.access.AccessReportingQuery;
import io.github.guillermodubon.coachgym.access.AccessReportingSummary;
import io.github.guillermodubon.coachgym.client.ClientReportingQuery;
import io.github.guillermodubon.coachgym.client.ClientReportingSummary;
import io.github.guillermodubon.coachgym.equipment.EquipmentBranchReportingSummary;
import io.github.guillermodubon.coachgym.equipment.EquipmentReportingQuery;
import io.github.guillermodubon.coachgym.equipment.EquipmentReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.IncidentBranchReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportingQuery;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceBranchReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceReportingQuery;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceReportingSummary;
import io.github.guillermodubon.coachgym.membership.MembershipReportingQuery;
import io.github.guillermodubon.coachgym.membership.MembershipReportingSummary;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryBranchReportingSummary;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryReportingQuery;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryReportingSummary;
import io.github.guillermodubon.coachgym.payment.PaidPaymentTrendPoint;
import io.github.guillermodubon.coachgym.payment.PaymentFinancialMetrics;
import io.github.guillermodubon.coachgym.payment.PaymentMethodDistribution;
import io.github.guillermodubon.coachgym.payment.PaymentReportingQuery;
import io.github.guillermodubon.coachgym.payment.PaymentTrendGranularity;
import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class ReportingQueryBoundaryContractTest {

    private static final List<Class<?>> PUBLIC_TYPES = List.of(
            ReportingQueryScope.class,
            ReportingQueryWindow.class,
            PaymentReportingQuery.class,
            PaymentFinancialMetrics.class,
            PaymentFinancialMetrics.CurrencyMetrics.class,
            PaymentMethodDistribution.class,
            PaidPaymentTrendPoint.class,
            PaymentTrendGranularity.class,
            ClientReportingQuery.class,
            ClientReportingSummary.class,
            MembershipReportingQuery.class,
            MembershipReportingSummary.class,
            MembershipReportingSummary.PlanDistribution.class,
            MembershipReportingSummary.CoverageDistribution.class,
            AccessReportingQuery.class,
            AccessReportingSummary.class,
            AccessBranchReportingSummary.class,
            AccessDailyTrendPoint.class,
            AccessOperationalDaySummary.class,
            EquipmentReportingQuery.class,
            EquipmentReportingSummary.class,
            EquipmentBranchReportingSummary.class,
            IncidentReportingQuery.class,
            IncidentReportingSummary.class,
            IncidentBranchReportingSummary.class,
            MaintenanceReportingQuery.class,
            MaintenanceReportingSummary.class,
            MaintenanceBranchReportingSummary.class,
            EmailDeliveryReportingQuery.class,
            EmailDeliveryReportingSummary.class,
            EmailDeliveryBranchReportingSummary.class);

    @Test
    void sourceContractsAreImmutableAndDoNotLeakFrameworkOrPersistenceTypes() {
        for (Class<?> type : PUBLIC_TYPES) {
            if (type.isRecord()) {
                for (RecordComponent component : type.getRecordComponents()) {
                    assertSafeType(component.getGenericType());
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                assertSafeType(method.getGenericReturnType());
                Arrays.stream(method.getGenericParameterTypes()).forEach(this::assertSafeType);
            }
        }
        assertThat(ClientReportingSummary.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("totalClients", "activeClients", "inactiveClients", "registeredInRange");
    }

    @Test
    void databaseAdaptersAreReadOnlyAndRemainInternal() throws Exception {
        for (String adapter : List.of(
                "io.github.guillermodubon.coachgym.payment.infrastructure.persistence.JdbcPaymentReportingQuery",
                "io.github.guillermodubon.coachgym.client.infrastructure.persistence.JdbcClientReportingQuery",
                "io.github.guillermodubon.coachgym.membership.infrastructure.persistence.JdbcMembershipReportingQuery",
                "io.github.guillermodubon.coachgym.access.infrastructure.persistence.JdbcAccessReportingQuery",
                "io.github.guillermodubon.coachgym.equipment.infrastructure.persistence.JdbcEquipmentReportingQuery",
                "io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence.JdbcIncidentReportingQuery",
                "io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence.JdbcMaintenanceReportingQuery",
                "io.github.guillermodubon.coachgym.notification.infrastructure.persistence.JdbcEmailDeliveryReportingQuery")) {
            Class<?> type = Class.forName(adapter);
            assertThat(java.lang.reflect.Modifier.isPublic(type.getModifiers())).isFalse();
            Transactional transaction = type.getAnnotation(Transactional.class);
            assertThat(transaction).isNotNull();
            assertThat(transaction.readOnly()).isTrue();
        }
    }

    private void assertSafeType(Type type) {
        String name = type.getTypeName();
        assertThat(name).doesNotContain(
                "org.springframework", "jakarta.persistence", "org.hibernate", "java.sql",
                "javax.sql", ".infrastructure", ".application", ".web", "JpaRepository",
                "JdbcTemplate", "ResultSet");
    }
}
