/**
 * Public contracts for durable client access credentials.
 *
 * <p>The module owns credential lifecycle semantics. The {@code access}
 * module will consume its public contracts when QR check-in is implemented;
 * it must not reach into this module's internal packages.</p>
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Access Credentials",
        allowedDependencies = {"auth", "client", "shared :: web", "user"})
package io.github.guillermodubon.coachgym.accesscredential;
