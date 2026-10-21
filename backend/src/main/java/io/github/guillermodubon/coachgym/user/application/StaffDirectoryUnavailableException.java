package io.github.guillermodubon.coachgym.user.application;

/** Safe boundary error when directory or authorization facts cannot be read. */
public final class StaffDirectoryUnavailableException extends RuntimeException {

    public StaffDirectoryUnavailableException() {
        super("Staff directory is temporarily unavailable.");
    }

    public StaffDirectoryUnavailableException(Throwable cause) {
        super("Staff directory is temporarily unavailable.", cause);
    }
}
