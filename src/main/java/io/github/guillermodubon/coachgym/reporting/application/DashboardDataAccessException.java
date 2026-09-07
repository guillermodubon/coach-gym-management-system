package io.github.guillermodubon.coachgym.reporting.application;

/** Wraps an unexpected failure while reading operational dashboard data. */
public class DashboardDataAccessException extends RuntimeException {

    public DashboardDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
