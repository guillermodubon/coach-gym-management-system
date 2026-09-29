package io.github.guillermodubon.coachgym.client;

/** Branch-attributed client counts; no personal or contact data is included. */
public record ClientReportingSummary(
        long totalClients,
        long activeClients,
        long inactiveClients,
        long registeredInRange) {

    public ClientReportingSummary {
        if (totalClients < 0 || activeClients < 0 || inactiveClients < 0
                || registeredInRange < 0) {
            throw new IllegalArgumentException("Client reporting counts must be non-negative.");
        }
        if (activeClients + inactiveClients != totalClients) {
            throw new IllegalArgumentException("Client status counts must reconcile to the total.");
        }
    }
}
