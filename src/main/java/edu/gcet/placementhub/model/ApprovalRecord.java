package edu.gcet.placementhub.model;

import java.sql.Timestamp;

/**
 * GCET PLACEMENT HUB - Approval Record Audit Model
 * Records every administrative and faculty approval/rejection decision.
 */
public class ApprovalRecord {
    public enum RequestType {
        ADMIN_PRIVILEGE,
        FACULTY_REGISTRATION,
        STUDENT_REGISTRATION,
        COMPANY_REGISTRATION
    }

    private String id;
    private RequestType requestType;
    private String targetId;
    private String targetName;
    private String approverId;
    private String approverName;
    private String approverRole;
    private String decision; // APPROVED, REJECTED, CORRECTION_REQUESTED
    private String comments;
    private Timestamp decidedAt;

    public ApprovalRecord() {}

    public ApprovalRecord(String id, RequestType requestType, String targetId, String targetName, String approverId, String approverName, String approverRole, String decision, String comments) {
        this.id = id;
        this.requestType = requestType;
        this.targetId = targetId;
        this.targetName = targetName;
        this.approverId = approverId;
        this.approverName = approverName;
        this.approverRole = approverRole;
        this.decision = decision;
        this.comments = comments;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public RequestType getRequestType() { return requestType; }
    public void setRequestType(RequestType requestType) { this.requestType = requestType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public String getApproverId() { return approverId; }
    public void setApproverId(String approverId) { this.approverId = approverId; }

    public String getApproverName() { return approverName; }
    public void setApproverName(String approverName) { this.approverName = approverName; }

    public String getApproverRole() { return approverRole; }
    public void setApproverRole(String approverRole) { this.approverRole = approverRole; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }

    public Timestamp getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Timestamp decidedAt) { this.decidedAt = decidedAt; }
}
