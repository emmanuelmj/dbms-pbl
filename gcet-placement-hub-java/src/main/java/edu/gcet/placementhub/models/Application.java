package edu.gcet.placementhub.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Placement application domain entity mapped from `applications` + `students` + `placement_drives`.
 */
public class Application {
    private String id;
    private String driveId;
    private String studentId;
    private String studentRollNumber;
    private String studentName;
    private String studentBranch;
    private double studentCgpa;
    private String companyId;
    private String companyName;
    private String jobRole;
    private ApplicationStage currentStage;
    private List<StageHistoryEntry> stageHistory = new ArrayList<>();
    private String appliedAt;
    private Double finalOfferLpa;
    private String rejectionReason;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDriveId() { return driveId; }
    public void setDriveId(String driveId) { this.driveId = driveId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getStudentRollNumber() { return studentRollNumber; }
    public void setStudentRollNumber(String studentRollNumber) { this.studentRollNumber = studentRollNumber; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentBranch() { return studentBranch; }
    public void setStudentBranch(String studentBranch) { this.studentBranch = studentBranch; }

    public double getStudentCgpa() { return studentCgpa; }
    public void setStudentCgpa(double studentCgpa) { this.studentCgpa = studentCgpa; }

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getJobRole() { return jobRole; }
    public void setJobRole(String jobRole) { this.jobRole = jobRole; }

    public ApplicationStage getCurrentStage() { return currentStage; }
    public void setCurrentStage(ApplicationStage currentStage) { this.currentStage = currentStage; }

    public List<StageHistoryEntry> getStageHistory() { return stageHistory; }
    public void setStageHistory(List<StageHistoryEntry> stageHistory) {
        this.stageHistory = stageHistory != null ? stageHistory : new ArrayList<>();
    }

    public String getAppliedAt() { return appliedAt; }
    public void setAppliedAt(String appliedAt) { this.appliedAt = appliedAt; }

    public Double getFinalOfferLpa() { return finalOfferLpa; }
    public void setFinalOfferLpa(Double finalOfferLpa) { this.finalOfferLpa = finalOfferLpa; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
