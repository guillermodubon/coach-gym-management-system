package io.github.guillermodubon.coachgym.reporting.application;

/** Safe translation of a failed source-owned reporting read. */
public final class ReportingDataAccessException extends RuntimeException {

    public ReportingDataAccessException(Throwable cause) {
        super("Reporting data could not be read.", cause);
    }
}
