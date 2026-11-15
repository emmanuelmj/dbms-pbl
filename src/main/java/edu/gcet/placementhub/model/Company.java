package edu.gcet.placementhub.model;

import java.sql.Timestamp;

/**
 * GCET PLACEMENT HUB - Company Model
 * Represents corporate recruiting partners with institutional approval status.
 */
public class Company {
    private String id;
    private String name;
    private String industry;
    private String website;
    private String location;
    private String description;
    private String hrName;
    private String hrEmail;
    private String hrPhone;
    private String tier = "Standard IT";
    private String userId;
    private String approvalStatus = "ACTIVE"; // PENDING, ACTIVE, REJECTED
    private String approvedByAdminId;
    private String approvedByAdminName;
    private Timestamp approvedAt;
    private String rejectionReason;
    private Timestamp createdAt;
    private String recruiterName;
    private String recruiterDesignation;
    private String companyLogo;
    private String linkedinUrl;

    public Company() {}

    public Company(String id, String name, String industry, String hrName, String hrEmail) {
        this.id = id;
        this.name = name;
        this.industry = industry;
        this.hrName = hrName;
        this.hrEmail = hrEmail;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getHrName() { return hrName; }
    public void setHrName(String hrName) { this.hrName = hrName; }

    public String getHrEmail() { return hrEmail; }
    public void setHrEmail(String hrEmail) { this.hrEmail = hrEmail; }

    public String getHrPhone() { return hrPhone; }
    public void setHrPhone(String hrPhone) { this.hrPhone = hrPhone; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }

    public boolean isApproved() { return "ACTIVE".equalsIgnoreCase(approvalStatus); }

    public String getApprovedByAdminId() { return approvedByAdminId; }
    public void setApprovedByAdminId(String approvedByAdminId) { this.approvedByAdminId = approvedByAdminId; }

    public String getApprovedByAdminName() { return approvedByAdminName; }
    public void setApprovedByAdminName(String approvedByAdminName) { this.approvedByAdminName = approvedByAdminName; }

    public Timestamp getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Timestamp approvedAt) { this.approvedAt = approvedAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getRecruiterName() { return recruiterName; }
    public void setRecruiterName(String recruiterName) { this.recruiterName = recruiterName; }

    public String getRecruiterDesignation() { return recruiterDesignation; }
    public void setRecruiterDesignation(String recruiterDesignation) { this.recruiterDesignation = recruiterDesignation; }

    public String getCompanyLogo() { return companyLogo; }
    public void setCompanyLogo(String companyLogo) { this.companyLogo = companyLogo; }

    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }

    @Override
    public String toString() {
        return name + " (" + industry + ")";
    }
}
