package io.github.guillermodubon.coachgym.user.application;

/** Safe persistence failure that never embeds SQL, bind values, or database detail. */
public class StaffIdentityDataAccessException extends RuntimeException {

    public StaffIdentityDataAccessException(String message) {
        super(message);
    }
}
