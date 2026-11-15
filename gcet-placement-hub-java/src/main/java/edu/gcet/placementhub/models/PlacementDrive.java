package edu.gcet.placementhub.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Campus placement drive entity mapped from `placement_drives`, `drive_eligible_branches`,
 * and `drive_required_skills`.
 */
public class PlacementDrive {
    private String id;
    private String companyId;
    private String companyName;
    private String jobRole;
    private String jobDescription;
    private double salaryPackageLpa;
    private List<String> eligibleBranches = new ArrayList<>();
    private double minCgpa;
    private int maxBacklogsAllowed;
    private int targetGraduationYear;
    private List<DriveRequiredSkill> requiredSkills = new ArrayList<>();
    private String driveDate;
    private String applicationDeadline;
    private int availablePositions;
    private String status;
    private String selectionProcess;
    private String createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getJobRole() { return jobRole; }
    public void setJobRole(String jobRole) { this.jobRole = jobRole; }

    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }

    public double getSalaryPackageLpa() { return salaryPackageLpa; }
    public void setSalaryPackageLpa(double salaryPackageLpa) { this.salaryPackageLpa = salaryPackageLpa; }

    public List<String> getEligibleBranches() { return eligibleBranches; }
    public void setEligibleBranches(List<String> eligibleBranches) {
        this.eligibleBranches = eligibleBranches != null ? eligibleBranches : new ArrayList<>();
    }

    public double getMinCgpa() { return minCgpa; }
    public void setMinCgpa(double minCgpa) { this.minCgpa = minCgpa; }

    public int getMaxBacklogsAllowed() { return maxBacklogsAllowed; }
    public void setMaxBacklogsAllowed(int maxBacklogsAllowed) { this.maxBacklogsAllowed = maxBacklogsAllowed; }

    public int getTargetGraduationYear() { return targetGraduationYear; }
    public void setTargetGraduationYear(int targetGraduationYear) { this.targetGraduationYear = targetGraduationYear; }

    public List<DriveRequiredSkill> getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(List<DriveRequiredSkill> requiredSkills) {
        this.requiredSkills = requiredSkills != null ? requiredSkills : new ArrayList<>();
    }

    public String getDriveDate() { return driveDate; }
    public void setDriveDate(String driveDate) { this.driveDate = driveDate; }

    public String getApplicationDeadline() { return applicationDeadline; }
    public void setApplicationDeadline(String applicationDeadline) { this.applicationDeadline = applicationDeadline; }

    public int getAvailablePositions() { return availablePositions; }
    public void setAvailablePositions(int availablePositions) { this.availablePositions = availablePositions; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSelectionProcess() { return selectionProcess; }
    public void setSelectionProcess(String selectionProcess) { this.selectionProcess = selectionProcess; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
