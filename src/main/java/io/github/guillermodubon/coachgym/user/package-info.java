/**
 * User-management ownership and public staff identity, scope, assignment, and
 * branch-context contracts.
 *
 * <p>The root package exposes safe, immutable self-profile projections and
 * policy types. Persistence, password encoders, sessions, storage providers,
 * and administrative mutation details remain internal to their owning
 * adapters. Self-profile commands derive their target from authentication and
 * never accept a caller-selected user id. Scope and assignment contracts are
 * immutable facts and pure policies; selecting an active branch is only a
 * context preference and never grants authorization. Session, persistence,
 * migration, and administrative orchestration details remain internal to
 * future adapters and application services.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "User Management")
package io.github.guillermodubon.coachgym.user;
