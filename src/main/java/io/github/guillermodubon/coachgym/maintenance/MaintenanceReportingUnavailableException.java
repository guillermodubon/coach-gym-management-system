package io.github.guillermodubon.coachgym.maintenance;

/** Safe failure for an unavailable maintenance reporting read. */
public final class MaintenanceReportingUnavailableException extends RuntimeException {

    public MaintenanceReportingUnavailableException(Throwable cause) {
        super("Maintenance reporting data could not be read.", cause);
    }
}
