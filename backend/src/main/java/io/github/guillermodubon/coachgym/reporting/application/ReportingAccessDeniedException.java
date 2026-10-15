package io.github.guillermodubon.coachgym.reporting.application;

/** Safe denial for unsupported reporting roles, scopes, or branch filters. */
public final class ReportingAccessDeniedException extends RuntimeException {

    public ReportingAccessDeniedException() {
        super("Reporting access is not available for the requested scope.");
    }
}
