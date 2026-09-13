package io.github.guillermodubon.coachgym.accesscredential.application;

/** Indicates a database uniqueness conflict without exposing protected data. */
public class AccessCredentialDuplicateException extends RuntimeException {

    public enum Kind {
        ACTIVE_CLIENT,
        CREDENTIAL_CODE,
        TOKEN_FINGERPRINT,
        UNKNOWN
    }

    private final Kind kind;

    public AccessCredentialDuplicateException(Kind kind) {
        super("Access credential already exists for the requested unique key.");
        this.kind = kind == null ? Kind.UNKNOWN : kind;
    }

    public Kind kind() {
        return kind;
    }
}
