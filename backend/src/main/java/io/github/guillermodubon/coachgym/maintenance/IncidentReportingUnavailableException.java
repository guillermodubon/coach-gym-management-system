package io.github.guillermodubon.coachgym.maintenance;

/** Safe failure for an unavailable incident reporting read. */
public final class IncidentReportingUnavailableException extends RuntimeException {

    public IncidentReportingUnavailableException(Throwable cause) {
        super("Incident reporting data could not be read.", cause);
    }
}
