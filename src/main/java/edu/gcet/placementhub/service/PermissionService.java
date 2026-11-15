package edu.gcet.placementhub.service;

import edu.gcet.placementhub.model.User;

/**
 * GCET PLACEMENT HUB - Centralized Role-Based Permission Service
 * Enforces business authorization rules in the backend service layer.
 */
public class PermissionService {

    public static boolean isTopLevelAdmin(User user) {
        if (user == null || !user.isActive()) return false;
        User.Role r = user.getRole();
        return r == User.Role.PRINCIPAL || r == User.Role.CHAIRMAN || r == User.Role.VICE_CHAIRMAN || r == User.Role.ADMIN;
    }

    /**
     * Rule: Only the Principal (Dr. K. Sagar) has authority to approve or reject new Administrator requests.
     * Chairman and Vice Chairman do NOT automatically have this privilege.
     */
    public static boolean canApproveAdminRequests(User user) {
        if (user == null || !user.isActive()) return false;
        return user.getRole() == User.Role.PRINCIPAL;
    }

    /**
     * Rule: New faculty registration requires dual approval:
     * 1. Administrator approval side (Principal, Chairman, Vice Chairman, or Admin)
     */
    public static boolean canApproveFacultyAdminSide(User user) {
        return isTopLevelAdmin(user);
    }

    /**
     * Rule: New faculty registration requires dual approval:
     * 2. Existing authorized faculty approval side
     */
    public static boolean canApproveFacultyFacultySide(User user) {
        if (user == null || !user.isActive()) return false;
        return user.getRole() == User.Role.FACULTY;
    }

    /**
     * Rule: Student registration is reviewed and approved by authorized faculty.
     */
    public static boolean canApproveStudentRegistration(User user) {
        if (user == null || !user.isActive()) return false;
        return user.getRole() == User.Role.FACULTY || user.getRole() == User.Role.PLACEMENT_OFFICER || isTopLevelAdmin(user);
    }

    /**
     * Rule: Corporate recruiter registration is reviewed and approved by administrators.
     */
    public static boolean canApproveCompanyRegistration(User user) {
        if (user == null || !user.isActive()) return false;
        return isTopLevelAdmin(user) || user.getRole() == User.Role.PLACEMENT_OFFICER;
    }

    /**
     * Rule: Managing student directory & records.
     */
    public static boolean canManageStudents(User user) {
        if (user == null || !user.isActive()) return false;
        return isTopLevelAdmin(user) || user.getRole() == User.Role.PLACEMENT_OFFICER;
    }

    /**
     * Rule: Modifying student profile.
     */
    public static boolean canEditStudent(User user, String targetRollNumber) {
        if (canManageStudents(user)) return true;
        if (user != null && user.getRole() == User.Role.STUDENT && user.getStudentRollNumber() != null) {
            return user.getStudentRollNumber().equalsIgnoreCase(targetRollNumber);
        }
        return false;
    }

    /**
     * Rule: Corporate drive publishing (must be approved company or placement officer/admin).
     */
    public static boolean canPublishPlacementDrives(User user) {
        if (user == null || !user.isActive()) return false;
        if (user.getRole() == User.Role.COMPANY) {
            return user.isActive();
        }
        return isTopLevelAdmin(user) || user.getRole() == User.Role.PLACEMENT_OFFICER;
    }

    /**
     * Rule: Technical skill matrix assessment & verification.
     */
    public static boolean canAssessSkills(User user) {
        if (user == null || !user.isActive()) return false;
        return isTopLevelAdmin(user) || user.getRole() == User.Role.PLACEMENT_OFFICER || user.getRole() == User.Role.FACULTY;
    }

    /**
     * Rule: User management and credentials provisioning.
     */
    public static boolean canManageUsers(User user) {
        return isTopLevelAdmin(user);
    }

    /**
     * Asserts permission or throws standard SecurityException.
     */
    public static void require(boolean condition, String actionDescription) {
        if (!condition) {
            throw new SecurityException("Permission Denied: Unauthorized attempt to " + actionDescription + ".");
        }
    }
}
