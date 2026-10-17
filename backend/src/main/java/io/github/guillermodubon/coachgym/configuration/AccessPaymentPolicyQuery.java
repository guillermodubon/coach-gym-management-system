package io.github.guillermodubon.coachgym.configuration;

/**
 * Public configuration-module boundary for the current access-payment policy.
 *
 * <p>The projection contains only the typed policy value and safe version
 * metadata. Access consumers do not depend on configuration persistence or
 * administrative application services.</p>
 */
@FunctionalInterface
public interface AccessPaymentPolicyQuery {

    /** Returns the current persisted access-payment policy projection. */
    AccessPaymentPolicyDetails findCurrent();
}
