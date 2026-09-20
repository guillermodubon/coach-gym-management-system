/**
 * User-management ownership and public staff identity contracts.
 *
 * <p>The root package exposes safe, immutable self-profile projections and
 * policy types. Persistence, password encoders, sessions, storage providers,
 * and administrative mutation details remain internal to their owning
 * adapters. Self-profile commands derive their target from authentication and
 * never accept a caller-selected user id.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "User Management")
package io.github.guillermodubon.coachgym.user;
