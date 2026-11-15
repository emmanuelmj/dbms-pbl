package edu.gcet.placementhub.models;

import java.util.List;

/**
 * Domain models and DTO bundles for Phase 5:
 * Approval Center, Analytics & Reports, Database Diagnostics, and Settings.
 */
public final class GovernanceModels {

    private GovernanceModels() {
    }

    public static class AdminRequest {
        private String id;
        private String userId;
        private String applicantName;
        private String applicantEmail;
        private String currentRole;
        private String requestedRole;
        private String department;
        private String reason;
        private String status;
        private String decidedBy;
        private String decidedByName;
        private String decisionNotes;
        private String createdAt;
        private String decidedAt;

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

        public String getDecidedBy() { return decidedBy; }
        public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }

        public String getDecidedByName() { return decidedByName; }
        public void setDecidedByName(String decidedByName) { this.decidedByName = decidedByName; }

        public String getDecisionNotes() { return decisionNotes; }
        public void setDecisionNotes(String decisionNotes) { this.decisionNotes = decisionNotes; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getDecidedAt() { return decidedAt; }
        public void setDecidedAt(String decidedAt) { this.decidedAt = decidedAt; }
    }

    public static class ApprovalRecord {
        private String id;
        private String requestType;
        private String targetId;
        private String targetName;
        private String studentRollNumber;
        private String department;
        private String approverId;
        private String approverName;
        private String approverRole;
        private String decision;
        private String comments;
        private String decidedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getRequestType() { return requestType; }
        public void setRequestType(String requestType) { this.requestType = requestType; }

        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }

        public String getTargetName() { return targetName; }
        public void setTargetName(String targetName) { this.targetName = targetName; }

        public String getStudentRollNumber() { return studentRollNumber; }
        public void setStudentRollNumber(String studentRollNumber) { this.studentRollNumber = studentRollNumber; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

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

        public String getDecidedAt() { return decidedAt; }
        public void setDecidedAt(String decidedAt) { this.decidedAt = decidedAt; }
    }

    public record ApprovalBundle(
            List<Student> students,
            List<Faculty> faculty,
            List<Company> companies,
            List<AdminRequest> adminRequests,
            List<ApprovalRecord> approvalHistory,
            String error
    ) {}

    public record TableDiagnostic(
            String tableName,
            boolean exists,
            int rowCount,
            String statusNote
    ) {}

    public record ExtendedDbStatus(
            DbStatus baseStatus,
            String mysqlVersion,
            long pingLatencyMs,
            List<TableDiagnostic> requiredTables,
            int availableTableCount,
            int totalRequiredTables
    ) {}

    public record ReportFilter(
            String academicYear,
            String graduationYear,
            String branch,
            String fromDate,
            String toDate
    ) {
        public static ReportFilter all() {
            return new ReportFilter("ALL", "ALL", "ALL", "", "");
        }
    }

    public record BranchReportRow(
            String branch,
            int totalStudents,
            int eligibleStudents,
            int placedStudents,
            int unplacedStudents,
            double placementRatePct,
            double avgCgpa,
            double avgPackageLpa,
            double highestPackageLpa
    ) {}

    public record CompanyReportRow(
            String companyId,
            String companyName,
            String industry,
            String tier,
            String approvalStatus,
            int drivesCount,
            int applicantsCount,
            int offersCount,
            double maxPackageLpa
    ) {}

    public record DriveReportRow(
            String driveId,
            String companyName,
            String jobRole,
            String status,
            String driveDate,
            String deadline,
            double minCgpa,
            int maxBacklogs,
            int targetGradYear,
            double salaryPackageLpa,
            int positions,
            int applicantsCount,
            int selectedCount
    ) {}

    public record PipelineReportRow(
            String stageCode,
            String stageLabel,
            int count,
            double percentage
    ) {}

    public record SkillReportRow(
            String skillId,
            String skillName,
            String category,
            int studentsWithSkill,
            int verifiedCount,
            int drivesRequiringSkill
    ) {}

    public record ReportBundle(
            ReportFilter appliedFilter,
            List<Student> scopedStudents,
            List<Company> scopedCompanies,
            List<PlacementDrive> scopedDrives,
            List<Application> scopedApplications,
            List<BranchReportRow> branchRows,
            List<CompanyReportRow> companyRows,
            List<DriveReportRow> driveRows,
            List<PipelineReportRow> pipelineRows,
            List<SkillReportRow> skillRows,
            int totalStudents,
            int eligibleStudents,
            int placedStudents,
            double placementRatePct,
            double avgPackageLpa,
            double highestPackageLpa,
            String scopeNotice,
            String error
    ) {}
}
