package edu.gcet.placementhub.models;

import java.util.Set;

/**
 * Exact match of GCET Placement Hub RBAC roles.
 */
public enum UserRole {
    PRINCIPAL,
    CHAIRMAN,
    VICE_CHAIRMAN,
    ADMIN,
    PLACEMENT_OFFICER,
    FACULTY,
    STUDENT,
    COMPANY;

    private static final Set<UserRole> ADMIN_ROLES = Set.of(PRINCIPAL, CHAIRMAN, VICE_CHAIRMAN, ADMIN);
    private static final Set<UserRole> PRIVILEGED_ROLES = Set.of(PRINCIPAL, CHAIRMAN, VICE_CHAIRMAN, ADMIN, PLACEMENT_OFFICER);

    public boolean isAdminRole() {
        return ADMIN_ROLES.contains(this);
    }

    public boolean isPrivilegedRole() {
        return PRIVILEGED_ROLES.contains(this);
    }

    public static UserRole fromString(String raw) {
        if (raw == null || raw.isBlank()) return STUDENT;
        String normalized = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        if ("RECRUITER".equals(normalized)) return COMPANY;
        if ("TPO".equals(normalized)) return PLACEMENT_OFFICER;
        if ("SUPER_ADMIN".equals(normalized) || "SYSADMIN".equals(normalized)) return ADMIN;
        try {
            return UserRole.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return STUDENT;
        }
    }
}
