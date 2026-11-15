package edu.gcet.placementhub.model;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * GCET PLACEMENT HUB - PlacementDrive Model
 */
public class PlacementDrive {
    private String id;
    private String companyId;
    private String companyName;
    private String jobRole;
    private String jobDescription;
    private double salaryPackageLpa;
    private double minCgpa;
    private int maxBacklogsAllowed;
    private int targetGraduationYear;
    private Date driveDate;
    private Date applicationDeadline;
    private int availablePositions;
    private String status; // PLANNED, UPCOMING, OPEN, CLOSED, COMPLETED, CANCELLED
    private String selectionProcess;
    private Timestamp createdAt;

    private List<String> eligibleBranches = new ArrayList<>();

    public PlacementDrive() {}

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

    public double getMinCgpa() { return minCgpa; }
    public void setMinCgpa(double minCgpa) { this.minCgpa = minCgpa; }

    public int getMaxBacklogsAllowed() { return maxBacklogsAllowed; }
    public void setMaxBacklogsAllowed(int maxBacklogsAllowed) { this.maxBacklogsAllowed = maxBacklogsAllowed; }

    public int getTargetGraduationYear() { return targetGraduationYear; }
    public void setTargetGraduationYear(int targetGraduationYear) { this.targetGraduationYear = targetGraduationYear; }

    public Date getDriveDate() { return driveDate; }
    public void setDriveDate(Date driveDate) { this.driveDate = driveDate; }

    public Date getApplicationDeadline() { return applicationDeadline; }
    public void setApplicationDeadline(Date applicationDeadline) { this.applicationDeadline = applicationDeadline; }

    public int getAvailablePositions() { return availablePositions; }
    public void setAvailablePositions(int availablePositions) { this.availablePositions = availablePositions; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSelectionProcess() { return selectionProcess; }
    public void setSelectionProcess(String selectionProcess) { this.selectionProcess = selectionProcess; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public List<String> getEligibleBranches() { return eligibleBranches; }
    public void setEligibleBranches(List<String> eligibleBranches) { this.eligibleBranches = eligibleBranches; }
}
