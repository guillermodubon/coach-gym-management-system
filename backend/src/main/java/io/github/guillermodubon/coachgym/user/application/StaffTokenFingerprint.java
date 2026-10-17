package io.github.guillermodubon.coachgym.user.application;

import java.util.Objects;
import java.util.regex.Pattern;

/** Persistable one-way token fingerprint; diagnostics intentionally redact its value. */
public final class StaffTokenFingerprint {

    private static final Pattern HEX_SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private final String value;
    private final String schemeVersion;

    public StaffTokenFingerprint(String value, String schemeVersion) {
        if (value == null || !HEX_SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException("Token fingerprint is invalid.");
        }
        this.value = value;
        this.schemeVersion = Objects.requireNonNull(schemeVersion, "Token scheme is required.");
        if (schemeVersion.length() > 64 || schemeVersion.isBlank()) {
            throw new IllegalArgumentException("Token scheme is invalid.");
        }
    }

    public String value() {
        return value;
    }

    public String schemeVersion() {
        return schemeVersion;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StaffTokenFingerprint fingerprint)) {
            return false;
        }
        return value.equals(fingerprint.value)
                && schemeVersion.equals(fingerprint.schemeVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, schemeVersion);
    }

    @Override
    public String toString() {
        return "StaffTokenFingerprint[schemeVersion=" + schemeVersion + ", value=<redacted>]";
    }
}
