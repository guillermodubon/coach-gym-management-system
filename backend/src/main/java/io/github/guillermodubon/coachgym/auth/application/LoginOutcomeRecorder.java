package io.github.guillermodubon.coachgym.auth.application;

/** Records login outcomes using finite categories and without identity values. */
public interface LoginOutcomeRecorder {

    enum Outcome {
        SUCCESS,
        REJECTED,
        RATE_LIMITED
    }

    void record(Outcome outcome);
}
