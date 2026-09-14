package io.github.guillermodubon.coachgym.access.application;

/**
 * Indicates that QR duplicate protection has not been configured for the
 * running deployment. The caller must configure the mandatory server-side
 * duplicate window; no fallback duration is safe.
 */
public final class AccessDuplicateScanPolicyUnavailableException
        extends RuntimeException {

    public AccessDuplicateScanPolicyUnavailableException() {
        super("QR duplicate-scan policy is not configured.");
    }
}
