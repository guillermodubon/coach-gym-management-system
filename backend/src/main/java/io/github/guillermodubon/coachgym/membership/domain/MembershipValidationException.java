package io.github.guillermodubon.coachgym.membership.domain;

public class MembershipValidationException
        extends RuntimeException {

    public MembershipValidationException(
            String message) {

        super(message);
    }
}
