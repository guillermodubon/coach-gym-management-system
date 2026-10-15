package io.github.guillermodubon.coachgym.reporting.application;

/** Raised when an operational dashboard query violates reporting rules. */
public class ReportingValidationException extends RuntimeException {

    public ReportingValidationException(String message) {
        super(message);
    }
}
