package io.github.guillermodubon.coachgym.configuration;

/**
 * Public security-context projection used by the settings web boundary.
 *
 * <p>The configuration module must not depend on authentication internals to
 * resolve the actor that is recorded for a policy change. Authentication
 * principals implement this small contract and expose only the immutable,
 * server-owned actor snapshot required by the policy application service.</p>
 */
@FunctionalInterface
public interface AccessPaymentPolicyActorProvider {

    /** Returns the authenticated actor snapshot for policy administration. */
    AccessPaymentPolicyActor accessPaymentPolicyActor();
}
