package io.github.guillermodubon.coachgym.user;

import java.util.Set;

/**
 * Explicit policy for fields that self-service staff profile commands may
 * change. The sets are immutable and use JSON/property names.
 */
public final class StaffSelfProfilePolicy {

    public static final Set<String> EDITABLE_FIELDS = Set.of("firstName", "lastName");
    public static final Set<String> PROHIBITED_FIELDS = Set.of(
            "username",
            "email",
            "role",
            "roles",
            "status",
            "passwordHash",
            "organizationScope",
            "branchAssignments",
            "permissions",
            "userId",
            "createdAt",
            "createdBy",
            "failedLoginCounters",
            "securityLockState",
            "auditRecords",
            "version");
    public static final Set<String> UNSUPPORTED_FIELDS = Set.of(
            "phone",
            "preferredLocale",
            "displayPreferences");

    private StaffSelfProfilePolicy() {
    }

    public static Set<String> editableFields() {
        return EDITABLE_FIELDS;
    }

    public static Set<String> prohibitedFields() {
        return PROHIBITED_FIELDS;
    }

    public static boolean isEditable(String fieldName) {
        return fieldName != null && EDITABLE_FIELDS.contains(fieldName);
    }
}
