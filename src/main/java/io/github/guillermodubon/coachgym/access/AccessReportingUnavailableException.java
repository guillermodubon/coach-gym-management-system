package io.github.guillermodubon.coachgym.access;

/** Safe failure for an unavailable access reporting read. */
public final class AccessReportingUnavailableException extends RuntimeException {

    public AccessReportingUnavailableException(Throwable cause) {
        super("Access reporting data could not be read.", cause);
    }
}
