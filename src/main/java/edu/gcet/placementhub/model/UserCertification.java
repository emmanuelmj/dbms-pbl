package edu.gcet.placementhub.model;

/**
 * Certification record for students and faculty members.
 */
public class UserCertification {
    private String id;
    private String userId;
    private String studentId;
    private String name;
    private String issuingOrg;
    private String issueDate;
    private String credentialId;
    private String credentialUrl;

    public UserCertification() {}

    public UserCertification(String id, String name, String issuingOrg, String issueDate, String credentialId, String credentialUrl) {
        this.id = id;
        this.name = name;
        this.issuingOrg = issuingOrg;
        this.issueDate = issueDate;
        this.credentialId = credentialId;
        this.credentialUrl = credentialUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIssuingOrg() { return issuingOrg; }
    public void setIssuingOrg(String issuingOrg) { this.issuingOrg = issuingOrg; }

    public String getIssueDate() { return issueDate; }
    public void setIssueDate(String issueDate) { this.issueDate = issueDate; }

    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }

    public String getCredentialUrl() { return credentialUrl; }
    public void setCredentialUrl(String credentialUrl) { this.credentialUrl = credentialUrl; }
}
