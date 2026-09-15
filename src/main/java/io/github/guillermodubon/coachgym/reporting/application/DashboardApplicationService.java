package io.github.guillermodubon.coachgym.reporting.application;

import io.github.guillermodubon.coachgym.reporting.AccessDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.DashboardNotificationDetails;
import io.github.guillermodubon.coachgym.reporting.MembershipDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.OperationalDashboardDetails;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public OperationalDashboardDetails getDashboard(
            DashboardQuery query,
            AuthenticatedActor actor) {
        Objects.requireNonNull(actor, "Authenticated actor is required.");

        DashboardPeriod period = periodResolver.resolve(query);
        DashboardSettings settings = settingsQuery.load();
        MembershipDashboardDetails memberships = membershipQuery.summarize(
                period, settings.membershipExpirationWarningDays());
        AccessDashboardDetails access = accessQuery.summarizeToday(period);
        DashboardNotificationDetails notifications =
                notificationQuery.summarize(actor.id());

        if (!isAdministrator()) {
            return OperationalDashboardDetails.forReceptionist(
                    clock.instant(), period.dates(), memberships, access, notifications);
        }

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

    private static boolean isAdministrator() {
        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (authentication == null) {
            return false;
        }

        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(
                                authority.getAuthority()));
    }
}
