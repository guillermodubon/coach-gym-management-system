/**
 * Public contracts for durable client access credentials.
 *
 * <p>The module owns credential lifecycle semantics. The {@code access}
 * module consumes {@link AccessCredentialResolver} and
 * {@link ResolvedAccessCredential} for QR check-in; it must not reach into
 * this module's internal packages.</p>
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Access Credentials",
        allowedDependencies = {"auth", "client", "shared :: web", "shared :: storage", "user"})
package io.github.guillermodubon.coachgym.accesscredential;
