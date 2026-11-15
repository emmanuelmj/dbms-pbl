package edu.gcet.placementhub.model;

import java.sql.Timestamp;

/**
 * GCET PLACEMENT HUB - Faculty Model
 * Represents faculty members, academic mentors, and departmental coordinators.
 */
public class Faculty {
    private String id;
    private String facultyId; // e.g. GCET-FAC-101
    private String employeeId;
    private String fullName;
    private String department;
    private String designation;
    private String email;
    private String phone;
    private String userId;
    private String username;
    private boolean facultyApproved = false;
    private String facultyApproverId;
    private String facultyApproverName;
    private Timestamp facultyApprovedAt;
    private boolean adminApproved = false;
    private String adminApproverId;
    private String adminApproverName;
    private Timestamp adminApprovedAt;
    private String status = "PENDING"; // PENDING, ACTIVE, REJECTED
    private String rejectionReason;
    private Timestamp createdAt;
    private String aboutMe;
    private String qualifications;
    private String areasOfExpertise;
    private String subjectsHandled;
    private String researchInterests;
    private String location;

    public Faculty() {
        this.status = "PENDING";
    }

    public Faculty(String id, String facultyId, String fullName, String department, String designation, String email, String phone) {
        this();
        this.id = id;
        this.facultyId = facultyId;
        this.fullName = fullName;
        this.department = department;
        this.designation = designation;
        this.email = email;
        this.phone = phone;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFacultyId() { return facultyId; }
    public void setFacultyId(String facultyId) { this.facultyId = facultyId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

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

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public boolean isFacultyApproved() { return facultyApproved; }
    public void setFacultyApproved(boolean facultyApproved) { this.facultyApproved = facultyApproved; }

    public String getFacultyApproverId() { return facultyApproverId; }
    public void setFacultyApproverId(String facultyApproverId) { this.facultyApproverId = facultyApproverId; }

    public String getFacultyApproverName() { return facultyApproverName; }
    public void setFacultyApproverName(String facultyApproverName) { this.facultyApproverName = facultyApproverName; }

    public Timestamp getFacultyApprovedAt() { return facultyApprovedAt; }
    public void setFacultyApprovedAt(Timestamp facultyApprovedAt) { this.facultyApprovedAt = facultyApprovedAt; }

    public boolean isAdminApproved() { return adminApproved; }
    public void setAdminApproved(boolean adminApproved) { this.adminApproved = adminApproved; }

    public String getAdminApproverId() { return adminApproverId; }
    public void setAdminApproverId(String adminApproverId) { this.adminApproverId = adminApproverId; }

    public String getAdminApproverName() { return adminApproverName; }
    public void setAdminApproverName(String adminApproverName) { this.adminApproverName = adminApproverName; }

    public Timestamp getAdminApprovedAt() { return adminApprovedAt; }
    public void setAdminApprovedAt(Timestamp adminApprovedAt) { this.adminApprovedAt = adminApprovedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getAboutMe() { return aboutMe; }
    public void setAboutMe(String aboutMe) { this.aboutMe = aboutMe; }

    public String getQualifications() { return qualifications; }
    public void setQualifications(String qualifications) { this.qualifications = qualifications; }

    public String getAreasOfExpertise() { return areasOfExpertise; }
    public void setAreasOfExpertise(String areasOfExpertise) { this.areasOfExpertise = areasOfExpertise; }

    public String getSubjectsHandled() { return subjectsHandled; }
    public void setSubjectsHandled(String subjectsHandled) { this.subjectsHandled = subjectsHandled; }

    public String getResearchInterests() { return researchInterests; }
    public void setResearchInterests(String researchInterests) { this.researchInterests = researchInterests; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean isFullyApproved() {
        return facultyApproved && adminApproved && isActive();
    }
}
