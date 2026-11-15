package edu.gcet.placementhub.model;

import java.sql.Timestamp;

/**
 * GCET PLACEMENT HUB - User Model
 * Represents authenticated users across 4 institutional roles:
 * ADMIN, PLACEMENT_OFFICER, FACULTY, STUDENT
 */
public class User {
    public enum Role {
        PRINCIPAL,
        CHAIRMAN,
        VICE_CHAIRMAN,
        ADMIN,
        PLACEMENT_OFFICER,
        FACULTY,
        STUDENT,
        COMPANY
    }

    private String id;
    private String username;
    private String email;
    private String fullName;
    private Role role;
    private String department;
    private String studentRollNumber;
    private String companyId;
    private String passwordHash;
    private String salt;
    private String status = "ACTIVE"; // PENDING, ACTIVE, REJECTED, SUSPENDED
    private boolean isActive = true;
    private boolean mustChangePassword = false;
    private Timestamp lastLogin;
    private Timestamp createdAt;

    public User() {}

    public User(String id, String username, String email, String fullName, Role role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.isActive = true;
        this.status = "ACTIVE";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getStudentRollNumber() { return studentRollNumber; }
    public void setStudentRollNumber(String studentRollNumber) { this.studentRollNumber = studentRollNumber; }

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public String getStatus() { return status; }
    public void setStatus(String status) {
        this.status = status;
        this.isActive = "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean isActive() { return isActive && "ACTIVE".equalsIgnoreCase(status); }
    public void setActive(boolean active) {
        this.isActive = active;
        if (!active && "ACTIVE".equalsIgnoreCase(this.status)) {
            this.status = "SUSPENDED";
        } else if (active && "SUSPENDED".equalsIgnoreCase(this.status)) {
            this.status = "ACTIVE";
        }
    }

    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public Timestamp getLastLogin() { return lastLogin; }
    public void setLastLogin(Timestamp lastLogin) { this.lastLogin = lastLogin; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    // ========================================================
    // Centralized RBAC Helpers
    // ========================================================

    public boolean isPrincipal() {
        return role == Role.PRINCIPAL;
    }

    public boolean isTopLevelAdmin() {
        return role == Role.PRINCIPAL || role == Role.CHAIRMAN || role == Role.VICE_CHAIRMAN || role == Role.ADMIN;
    }

    public boolean canApproveAdminRequests() {
        // ONLY the Principal (Dr. K. Sagar) has exclusive authority to approve additional administrators
        return role == Role.PRINCIPAL;
    }

    public boolean canApproveFacultyAdminSide() {
        return isTopLevelAdmin();
    }

    public boolean canApproveFacultyFacultySide() {
        return role == Role.FACULTY && isActive();
    }

    public boolean canApproveStudents() {
        return role == Role.FACULTY || role == Role.PLACEMENT_OFFICER || isTopLevelAdmin();
    }

    public boolean canApproveCompanies() {
        return isTopLevelAdmin() || role == Role.PLACEMENT_OFFICER;
    }

    public boolean canManageStudents() {
        return isTopLevelAdmin() || role == Role.PLACEMENT_OFFICER;
    }

    public boolean canEditStudent(String targetRollNumber) {
        if (canManageStudents()) return true;
        return role == Role.STUDENT && studentRollNumber != null && studentRollNumber.equalsIgnoreCase(targetRollNumber);
    }

    public boolean canManageUsers() {
        return isTopLevelAdmin();
    }

    public boolean canManageFaculty() {
        return isTopLevelAdmin() || role == Role.PLACEMENT_OFFICER;
    }

    public boolean canManageSkillLibrary() {
        return isTopLevelAdmin() || role == Role.PLACEMENT_OFFICER;
    }

    public boolean canAssessSkills() {
        return isTopLevelAdmin() || role == Role.PLACEMENT_OFFICER || role == Role.FACULTY;
    }
}
