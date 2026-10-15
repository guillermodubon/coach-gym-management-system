package io.github.guillermodubon.coachgym.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.reporting.application.ReportingCompositionApplicationService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class ReportingCompositionArchitectureContractTest {

    private static final List<Class<?>> PUBLIC_RECORDS = List.of(
            ReportingDashboardRequest.class,
            BranchComparisonRequest.class,
            ReportingTrendRequest.class,
            AdministratorReportingMetrics.class,
            AdministratorReportingDashboardSummary.class,
            ReceptionistMembershipSummary.class,
            ReceptionistAccessSummary.class,
            ReceptionistReportingDashboardSummary.class,
            ReportingBranchIdentity.class,
            BranchComparisonRow.class,
            BranchComparisonReport.class,
            PaidFinancialTrendReport.class,
            AccessTrendReport.class);

    @Test
    void compositionServiceDependsOnPublicPortsNotOtherModulesInternals() {
        for (Field field : ReportingCompositionApplicationService.class.getDeclaredFields()) {
            assertThat(field.getType().getName())
                    .as("field %s", field.getName())
                    .doesNotContain(".infrastructure.", ".web.", "JpaRepository", "JdbcTemplate");
        }
        assertThat(ReportingCompositionApplicationService.class.getDeclaredFields())
                .extracting(field -> field.getType().getName())
                .noneMatch(type -> type.contains("DashboardNotificationQuery"));
    }

    @Test
    void publicCompositionContractsAreImmutableAndFrameworkNeutral() {
        assertThat(ReportingDashboardSummary.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(
                        AdministratorReportingDashboardSummary.class,
                        ReceptionistReportingDashboardSummary.class);
        for (Class<?> type : PUBLIC_RECORDS) {
            assertThat(type.isRecord()).as("%s remains an immutable record", type.getName()).isTrue();
            for (RecordComponent component : type.getRecordComponents()) {
                assertSafeType(component.getGenericType().getTypeName());
            }
        }
    }

    @Test
    void compositionEntrypointsAreProtectedAndReadOnly() throws Exception {
        Transactional transaction =
                ReportingCompositionApplicationService.class.getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        for (Method method : List.of(
                ReportingCompositionApplicationService.class.getMethod(
                        "dashboard", ReportingDashboardRequest.class,
                        io.github.guillermodubon.coachgym.user.AuthenticatedActor.class),
                ReportingCompositionApplicationService.class.getMethod(
                        "compareBranches", BranchComparisonRequest.class,
                        io.github.guillermodubon.coachgym.user.AuthenticatedActor.class),
                ReportingCompositionApplicationService.class.getMethod(
                        "paidTrend", ReportingTrendRequest.class,
                        io.github.guillermodubon.coachgym.user.AuthenticatedActor.class),
                ReportingCompositionApplicationService.class.getMethod(
                        "accessTrend", ReportingTrendRequest.class,
                        io.github.guillermodubon.coachgym.user.AuthenticatedActor.class))) {
            assertThat(method.getAnnotation(PreAuthorize.class).value())
                    .isEqualTo("hasAnyRole('ADMIN', 'RECEPTIONIST')");
        }
    }

    private static void assertSafeType(String typeName) {
        assertThat(typeName).doesNotContain(
                "org.springframework", "jakarta.persistence", "org.hibernate", "java.sql",
                "javax.sql", ".infrastructure", ".application", ".web", "JdbcTemplate",
                "JpaRepository", "ResultSet");
    }
}
