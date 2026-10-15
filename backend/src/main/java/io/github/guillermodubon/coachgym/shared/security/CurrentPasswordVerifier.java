package io.github.guillermodubon.coachgym.shared.security;

import java.util.UUID;

/** Auth-owned verification boundary for a recently supplied current password. */
public interface CurrentPasswordVerifier {

    /**
     * Verifies the current credentials of the identified authenticated actor.
     * Implementations must not retain or log the supplied password.
     */
    boolean verify(UUID actorId, String actorUsername, String currentPassword);
}
