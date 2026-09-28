package io.github.guillermodubon.coachgym.user.application;

/** Produces a short-lived opaque secret; callers must never persist or log it. */
public interface StaffOneTimeTokenGenerator {

    String generate();
}
