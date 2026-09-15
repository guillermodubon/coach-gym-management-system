package io.github.guillermodubon.coachgym.accesscredential;

/** Lifecycle states of a permanent client access credential. */
public enum AccessCredentialStatus {

    /** The credential can be presented for a future access decision. */
    ACTIVE,

    /** The credential is permanently unusable. */
    REVOKED
}
