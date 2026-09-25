package io.github.guillermodubon.coachgym.access.application;

/**
 * Indicates that branch-aware duplicate protection has not been configured
 * for the running deployment. The caller must configure the server-side
 * duplicate window; no fallback duration is safe.
 */
public final class AccessDuplicateScanPolicyUnavailableException
        extends RuntimeException {

    public AccessDuplicateScanPolicyUnavailableException() {
        super("Access duplicate-scan policy is not configured.");
    }
}
