package io.github.guillermodubon.coachgym.access.domain;

/** Outcome of evaluating a recently persisted access attempt. */
public enum DuplicateScanResult {

    /** No equivalent recent attempt exists; normal access evaluation may run. */
    NOT_DUPLICATE,

    /** An equivalent attempt falls inside the configured duplicate window. */
    DUPLICATE
}
