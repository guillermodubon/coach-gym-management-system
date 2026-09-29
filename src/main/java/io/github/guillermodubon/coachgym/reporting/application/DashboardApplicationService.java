package io.github.guillermodubon.coachgym.reporting.application;

import io.github.guillermodubon.coachgym.reporting.AccessDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.DashboardNotificationDetails;
import io.github.guillermodubon.coachgym.reporting.MembershipDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.OperationalDashboardDetails;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import java.time.Clock;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Composes the role-aware, read-only operational dashboard. */
@Service
public class DashboardApplicationService {

    private final DashboardPeriodResolver periodResolver;
    private final DashboardSettingsQuery settingsQuery;
    private final MembershipDashboardQuery membershipQuery;
    private final AccessDashboardQuery accessQuery;
    private final PaymentDashboardQuery paymentQuery;
    private final EquipmentDashboardQuery equipmentQuery;
    private final IncidentDashboardQuery incidentQuery;
    private final MaintenanceDashboardQuery maintenanceQuery;
    private final DashboardNotificationQuery notificationQuery;
    private final StaffScopeQuery staffScopeQuery;
    private final Clock clock;

    public DashboardApplicationService(
            DashboardPeriodResolver periodResolver,
            DashboardSettingsQuery settingsQuery,
            MembershipDashboardQuery membershipQuery,
            AccessDashboardQuery accessQuery,
            PaymentDashboardQuery paymentQuery,
            EquipmentDashboardQuery equipmentQuery,
            IncidentDashboardQuery incidentQuery,
            MaintenanceDashboardQuery maintenanceQuery,
            DashboardNotificationQuery notificationQuery,
            StaffScopeQuery staffScopeQuery,
            Clock clock) {
        this.periodResolver = Objects.requireNonNull(periodResolver);
        this.settingsQuery = Objects.requireNonNull(settingsQuery);
        this.membershipQuery = Objects.requireNonNull(membershipQuery);
        this.accessQuery = Objects.requireNonNull(accessQuery);
        this.paymentQuery = Objects.requireNonNull(paymentQuery);
        this.equipmentQuery = Objects.requireNonNull(equipmentQuery);
        this.incidentQuery = Objects.requireNonNull(incidentQuery);
        this.maintenanceQuery = Objects.requireNonNull(maintenanceQuery);
        this.notificationQuery = Objects.requireNonNull(notificationQuery);
        this.staffScopeQuery = Objects.requireNonNull(staffScopeQuery);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public OperationalDashboardDetails getDashboard(
            DashboardQuery query,
            AuthenticatedActor actor) {
        requireOrganizationAdmin(actor);

        DashboardPeriod period = periodResolver.resolve(query);
        DashboardSettings settings = settingsQuery.load();
        MembershipDashboardDetails memberships = membershipQuery.summarize(
                period, settings.membershipExpirationWarningDays());
        AccessDashboardDetails access = accessQuery.summarizeToday(period);
        DashboardNotificationDetails notifications =
                notificationQuery.summarize(actor.id());

        return OperationalDashboardDetails.forAdministrator(
                clock.instant(),
                period.dates(),
                memberships,
                access,
                paymentQuery.summarize(period, settings.currency()),
                equipmentQuery.summarize(),
                incidentQuery.summarize(),
                maintenanceQuery.summarize(period.operationalDate()),
                notifications);
    }

    private void requireOrganizationAdmin(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null) {
            throw new ReportingAccessDeniedException();
        }
        StaffAuthorizationContext authorization;
        try {
            authorization = staffScopeQuery.findAuthorizationContext(actor.id()).orElse(null);
        } catch (RuntimeException exception) {
            throw new DashboardDataAccessException(
                    "Operational dashboard authorization could not be resolved.", exception);
        }
        if (authorization == null
                || !actor.id().equals(authorization.userId())
                || !authorization.organizationAdmin()) {
            throw new ReportingAccessDeniedException();
        }
    }
}
