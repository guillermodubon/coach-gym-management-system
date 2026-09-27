package io.github.guillermodubon.coachgym.user.application;

/** Creates domain-separated, versioned fingerprints for one-time staff tokens. */
public interface StaffTokenProtector {

    StaffTokenFingerprint fingerprint(String token, StaffTokenPurpose purpose);

    boolean matches(String token, StaffTokenFingerprint expected, StaffTokenPurpose purpose);
}
