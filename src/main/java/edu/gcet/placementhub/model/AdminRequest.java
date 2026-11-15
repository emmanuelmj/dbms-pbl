package edu.gcet.placementhub.model;

import java.sql.Timestamp;

/**
 * GCET PLACEMENT HUB - Administrator Privilege Request Model
 * Represents a formal petition by an existing institutional user for elevated admin privileges.
 * Exclusively reviewed and decided by the Principal (Dr. K. Sagar).
 */
public class AdminRequest {
    private String id;
    private String userId;
    private String applicantName;
    private String applicantEmail;
    private String currentRole;
    private String requestedRole = "ADMIN";
    private String department;
    private String reason;
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED
    private String decidedByPrincipalId;
    private String decidedByPrincipalName;
    private Timestamp decisionDate;
    private String decisionNotes;
    private Timestamp createdAt;

    public AdminRequest() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }

    public String getApplicantEmail() { return applicantEmail; }
    public void setApplicantEmail(String applicantEmail) { this.applicantEmail = applicantEmail; }

    public String getCurrentRole() { return currentRole; }
    public void setCurrentRole(String currentRole) { this.currentRole = currentRole; }

    public String getRequestedRole() { return requestedRole; }
    public void setRequestedRole(String requestedRole) { this.requestedRole = requestedRole; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isPending() { return "PENDING".equalsIgnoreCase(status); }

    public String getDecidedByPrincipalId() { return decidedByPrincipalId; }
    public void setDecidedByPrincipalId(String decidedByPrincipalId) { this.decidedByPrincipalId = decidedByPrincipalId; }

    public String getDecidedByPrincipalName() { return decidedByPrincipalName; }
    public void setDecidedByPrincipalName(String decidedByPrincipalName) { this.decidedByPrincipalName = decidedByPrincipalName; }

    public Timestamp getDecisionDate() { return decisionDate; }
    public void setDecisionDate(Timestamp decisionDate) { this.decisionDate = decisionDate; }

    public String getDecisionNotes() { return decisionNotes; }
    public void setDecisionNotes(String decisionNotes) { this.decisionNotes = decisionNotes; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
