package io.github.guillermodubon.coachgym.user;

/**
 * Staff roles supported by the v1 authorization model.
 *
 * <p>External equipment technicians do not require application accounts.
 * Administrators record and manage maintenance operations, while
 * receptionists receive only the explicitly approved operational
 * permissions.
 */
public enum RoleCode {
    ADMIN,
    RECEPTIONIST
}
