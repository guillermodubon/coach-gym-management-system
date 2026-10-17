package io.github.guillermodubon.coachgym.reporting;

import io.github.guillermodubon.coachgym.access.AccessReportingSummary;
import io.github.guillermodubon.coachgym.client.ClientReportingSummary;
import io.github.guillermodubon.coachgym.equipment.EquipmentReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceReportingSummary;
import io.github.guillermodubon.coachgym.membership.MembershipReportingSummary;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryReportingSummary;
import io.github.guillermodubon.coachgym.payment.PaymentFinancialMetrics;
import java.util.Objects;

/** Bounded administrator-only dashboard sections composed from source modules. */
public record AdministratorReportingMetrics(
        PaymentFinancialMetrics financial,
        ClientReportingSummary clients,
        MembershipReportingSummary memberships,
        AccessReportingSummary access,
        EquipmentReportingSummary equipment,
        IncidentReportingSummary incidents,
        MaintenanceReportingSummary maintenance,
        EmailDeliveryReportingSummary durableEmail) {

    public AdministratorReportingMetrics {
        Objects.requireNonNull(financial, "Financial metrics are required.");
        Objects.requireNonNull(clients, "Client metrics are required.");
        Objects.requireNonNull(memberships, "Membership metrics are required.");
        Objects.requireNonNull(access, "Access metrics are required.");
        Objects.requireNonNull(equipment, "Equipment metrics are required.");
        Objects.requireNonNull(incidents, "Incident metrics are required.");
        Objects.requireNonNull(maintenance, "Maintenance metrics are required.");
        Objects.requireNonNull(durableEmail, "Durable email metrics are required.");
    }
}
