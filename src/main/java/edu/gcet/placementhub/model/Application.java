package edu.gcet.placementhub.model;

import java.sql.Timestamp;

/**
 * GCET PLACEMENT HUB - Application Model (10-Stage Recruitment Funnel)
 */
public class Application {
    private String id;
    private String driveId;
    private String studentId;
    private String companyName;
    private String jobRole;
    private String studentName;
    private String studentRollNumber;
    private String studentBranch;
    private double studentCgpa;
    private String currentStage;
    private Timestamp appliedAt;
    private Double finalOfferLpa;
    private String rejectionReason;

    public Application() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDriveId() { return driveId; }
    public void setDriveId(String driveId) { this.driveId = driveId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getJobRole() { return jobRole; }
    public void setJobRole(String jobRole) { this.jobRole = jobRole; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentRollNumber() { return studentRollNumber; }
    public void setStudentRollNumber(String studentRollNumber) { this.studentRollNumber = studentRollNumber; }

    public String getStudentBranch() { return studentBranch; }
    public void setStudentBranch(String studentBranch) { this.studentBranch = studentBranch; }

    public double getStudentCgpa() { return studentCgpa; }
    public void setStudentCgpa(double studentCgpa) { this.studentCgpa = studentCgpa; }

    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }

    public Timestamp getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Timestamp appliedAt) { this.appliedAt = appliedAt; }

    public Double getFinalOfferLpa() { return finalOfferLpa; }
    public void setFinalOfferLpa(Double finalOfferLpa) { this.finalOfferLpa = finalOfferLpa; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
