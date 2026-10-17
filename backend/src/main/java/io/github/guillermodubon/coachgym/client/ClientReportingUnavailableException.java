package io.github.guillermodubon.coachgym.client;

/** Safe failure for an unavailable client reporting read. */
public final class ClientReportingUnavailableException extends RuntimeException {

    public ClientReportingUnavailableException(Throwable cause) {
        super("Client reporting data could not be read.", cause);
    }
}
