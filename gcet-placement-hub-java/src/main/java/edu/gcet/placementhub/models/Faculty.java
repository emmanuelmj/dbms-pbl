package edu.gcet.placementhub.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Faculty coordinator directory entry mapped from MySQL `faculty` and `users` tables.
 */
public class Faculty {
    private String id;
    private String userId;
    private String username;
    private String employeeId;
    private String fullName;
    private String department;
    private String designation;
    private String email;
    private String phone;
    private List<String> authorizedDepartments = new ArrayList<>();
    private boolean facultyApproved;
    private boolean adminApproved;
    private String approvedByName;
    private String rejectionReason;
    private String status;
    private boolean active;
    private String createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getFacultyId() { return employeeId; }
    public void setFacultyId(String facultyId) { this.employeeId = facultyId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public List<String> getAuthorizedDepartments() { return authorizedDepartments; }
    public void setAuthorizedDepartments(List<String> authorizedDepartments) {
        this.authorizedDepartments = authorizedDepartments != null ? authorizedDepartments : new ArrayList<>();
    }

    public boolean isFacultyApproved() { return facultyApproved; }
    public void setFacultyApproved(boolean facultyApproved) { this.facultyApproved = facultyApproved; }

    public boolean isAdminApproved() { return adminApproved; }
    public void setAdminApproved(boolean adminApproved) { this.adminApproved = adminApproved; }

    public String getApprovedByName() { return approvedByName; }
    public void setApprovedByName(String approvedByName) { this.approvedByName = approvedByName; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
