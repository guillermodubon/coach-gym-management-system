/**
 * Public organization and gym-branch contracts.
 *
 * <p>This module owns the single Coach Gym organization model, its public
 * branch projections, application use cases, persistence adapters, and the
 * canonical organization/branch HTTP administration boundary. Staff authority
 * is derived from persisted user scope and assignment contracts by the owning
 * consuming modules; selecting a branch alone never grants access.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Organization and Branches")
package io.github.guillermodubon.coachgym.organization;
