package io.github.guillermodubon.coachgym.organization;

/** Safe failure when canonical branch metadata cannot be read for reporting. */
public final class GymBranchReportingUnavailableException extends RuntimeException {

    public GymBranchReportingUnavailableException(Throwable cause) {
        super("Reporting branch metadata could not be read.", cause);
    }
}
