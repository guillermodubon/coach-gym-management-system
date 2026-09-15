package io.github.guillermodubon.coachgym.reporting;

import java.time.Instant;

/**
 * Complete immutable operational dashboard projection.
 *
 * <p>Membership, access, and notification sections are always present.
 * Administrative sections are null only when intentionally omitted for a
 * RECEPTIONIST projection.</p>
 */
public record OperationalDashboardDetails(
        Instant generatedAt,
        DashboardPeriodDetails period,
        MembershipDashboardDetails memberships,
        AccessDashboardDetails access,
        PaymentDashboardDetails payments,
        EquipmentDashboardDetails equipment,
        IncidentDashboardDetails incidents,
        MaintenanceDashboardDetails maintenance,
        DashboardNotificationDetails notifications) {

    public OperationalDashboardDetails {
        if (generatedAt == null) {
            throw new IllegalArgumentException(
                    "Dashboard generation timestamp is required.");
        }
        if (period == null) {
            throw new IllegalArgumentException("Dashboard period is required.");
        }
        if (memberships == null) {
            throw new IllegalArgumentException(
                    "Dashboard membership metrics are required.");
        }
        if (access == null) {
            throw new IllegalArgumentException(
                    "Dashboard access metrics are required.");
        }
        if (notifications == null) {
            throw new IllegalArgumentException(
                    "Dashboard notification metrics are required.");
        }
    }

    public static OperationalDashboardDetails forAdministrator(
            Instant generatedAt,
            DashboardPeriodDetails period,
            MembershipDashboardDetails memberships,
            AccessDashboardDetails access,
            PaymentDashboardDetails payments,
            EquipmentDashboardDetails equipment,
            IncidentDashboardDetails incidents,
            MaintenanceDashboardDetails maintenance,
            DashboardNotificationDetails notifications) {
        if (payments == null || equipment == null
                || incidents == null || maintenance == null) {
            throw new IllegalArgumentException(
                    "Administrator dashboard requires every administrative section.");
        }
        return new OperationalDashboardDetails(
                generatedAt,
                period,
                memberships,
                access,
                payments,
                equipment,
                incidents,
                maintenance,
                notifications);
    }

    public static OperationalDashboardDetails forReceptionist(
            Instant generatedAt,
            DashboardPeriodDetails period,
            MembershipDashboardDetails memberships,
            AccessDashboardDetails access,
            DashboardNotificationDetails notifications) {
        return new OperationalDashboardDetails(
                generatedAt,
                period,
                memberships,
                access,
                null,
                null,
                null,
                null,
                notifications);
    }

    public boolean administrativeSectionsIncluded() {
        return payments != null
                && equipment != null
                && incidents != null
                && maintenance != null;
    }
}
