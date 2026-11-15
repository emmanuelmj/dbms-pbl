package edu.gcet.placementhub.services;

import edu.gcet.placementhub.dao.*;
import edu.gcet.placementhub.database.DatabaseConnection;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.models.PlacementModels.EligibilityResult;
import edu.gcet.placementhub.models.PlacementModels.PlacementBundle;
import edu.gcet.placementhub.services.StudentManagementService.OperationResult;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Pure JDBC Phase 4 service managing Companies, Placement Drives,
 * Eligibility-Gated Student Applications, and the 10-Stage Recruitment Pipeline.
 */
public class PlacementManagementService {

    private final CompanyDao companyDao = new CompanyDao();
    private final DriveDao driveDao = new DriveDao();
    private final StudentDao studentDao = new StudentDao();
    private final SkillDao skillDao = new SkillDao();
    private final ApplicationDao applicationDao = new ApplicationDao();
    private final AuditLogDao auditLogDao = new AuditLogDao();

    public PlacementBundle loadPlacementBundle(User actor) {
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            List<Company> companies = companyDao.findAll(conn);
            List<PlacementDrive> drives = driveDao.findAll(conn);
            List<Skill> skills = skillDao.findAllSkills(conn);

            List<Student> students;
            if (actor != null && actor.getRole() == UserRole.STUDENT) {
                Student self = null;
                if (actor.getStudentRollNumber() != null && !actor.getStudentRollNumber().isBlank()) {
                    self = studentDao.findByIdOrRoll(conn, actor.getStudentRollNumber());
                }
                if (self == null && actor.getId() != null) {
                    Student byUser = studentDao.findByUserId(conn, actor.getId());
                    if (byUser != null) {
                        self = studentDao.findByIdOrRoll(conn, byUser.getId());
                    }
                }
                students = self != null ? List.of(self) : List.of();
            } else {
                students = studentDao.findAll(conn);
            }

            List<Application> applications = applicationDao.findAllForUser(conn, actor);
            return new PlacementBundle(companies, drives, students, skills, applications, null);
        } catch (SQLException ex) {
            return new PlacementBundle(List.of(), List.of(), List.of(), List.of(), List.of(),
                    "Failed to load placement records from MySQL: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // COMPANY MANAGEMENT
    // -------------------------------------------------------------------------

    public OperationResult<String> createCompany(User actor, Company draft) {
        if (actor == null || !actor.getRole().isPrivilegedRole()) {
            return OperationResult.fail("Only Placement Officers or College Administrators may add corporate partners.");
        }
        if (draft == null || isBlank(draft.getName()) || isBlank(draft.getIndustry())) {
            return OperationResult.fail("Company Name and Industry are required.");
        }

        String cleanName = draft.getName().trim();
        String id = "comp-" + UUID.randomUUID();
        draft.setId(id);
        draft.setName(cleanName);
        draft.setIndustry(draft.getIndustry().trim());

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            if (companyDao.existsByName(conn, cleanName, null)) {
                return OperationResult.fail("Company \"" + cleanName + "\" already exists in MySQL.");
            }
            conn.setAutoCommit(false);
            try {
                companyDao.insertCompany(conn, draft, actor.getId(), actor.getFullName());
                auditLogDao.insertLog(conn, "COMPANY_CREATED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                        "Added corporate partner \"" + cleanName + "\" to MySQL.");
                conn.commit();
                return OperationResult.ok("Saved partner company \"" + cleanName + "\" in MySQL.", id);
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to register company: " + ex.getMessage());
        }
    }

    public OperationResult<Void> updateCompany(User actor, Company updated) {
        if (actor == null || updated == null || isBlank(updated.getId())) {
            return OperationResult.fail("Invalid company update request.");
        }
        boolean isOwnCompany = actor.getRole() == UserRole.COMPANY
                && actor.getCompanyId() != null
                && actor.getCompanyId().equals(updated.getId());
        if (!actor.getRole().isPrivilegedRole() && !isOwnCompany) {
            return OperationResult.fail("Only Placement Administrators or the authorized Company Recruiter may edit this profile.");
        }
        if (isBlank(updated.getName()) || isBlank(updated.getIndustry())) {
            return OperationResult.fail("Company Name and Industry cannot be empty.");
        }

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Company existing = companyDao.findById(conn, updated.getId());
            if (existing == null) {
                return OperationResult.fail("Company record not found in MySQL.");
            }
            String cleanName = updated.getName().trim();
            if (companyDao.existsByName(conn, cleanName, existing.getId())) {
                return OperationResult.fail("Another company already uses the name \"" + cleanName + "\".");
            }
            updated.setName(cleanName);
            updated.setIndustry(updated.getIndustry().trim());

            conn.setAutoCommit(false);
            try {
                companyDao.updateCompany(conn, updated);
                auditLogDao.insertLog(conn, "COMPANY_UPDATED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                        "Updated corporate partner \"" + cleanName + "\" (" + existing.getId() + ").");
                conn.commit();
                return OperationResult.ok("Updated MySQL record for \"" + cleanName + "\".", null);
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to update company: " + ex.getMessage());
        }
    }

    public OperationResult<Void> updateCompanyApprovalStatus(User actor, String companyId, String approvalStatus, String reason) {
        if (actor == null || !actor.getRole().isPrivilegedRole()) {
            return OperationResult.fail("Only Placement Officers or College Administrators may change company approval status.");
        }
        if (isBlank(companyId) || isBlank(approvalStatus)) {
            return OperationResult.fail("Company ID and target approval status are required.");
        }
        String norm = approvalStatus.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "APPROVED", "PENDING_APPROVAL", "REJECTED").contains(norm)) {
            return OperationResult.fail("Unsupported company status: " + norm);
        }
        if ("REJECTED".equals(norm) && isBlank(reason)) {
            return OperationResult.fail("Please enter a clear reason when rejecting a company.");
        }

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Company existing = companyDao.findById(conn, companyId);
            if (existing == null) {
                return OperationResult.fail("Company record not found in MySQL.");
            }
            conn.setAutoCommit(false);
            try {
                companyDao.updateApprovalStatus(conn, existing.getId(), norm, reason, actor.getId(), actor.getFullName());
                auditLogDao.insertLog(conn, "COMPANY_APPROVAL_UPDATED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                        "Set company \"" + existing.getName() + "\" approval status to " + norm + ".");
                conn.commit();
                return OperationResult.ok("Updated approval status for \"" + existing.getName() + "\" to " + norm + ".", null);
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to update company approval: " + ex.getMessage());
        }
    }

    public OperationResult<Void> deleteCompany(User actor, String companyId) {
        if (actor == null || !actor.getRole().isPrivilegedRole()) {
            return OperationResult.fail("Only Placement Officers or College Administrators may delete companies.");
        }
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Company existing = companyDao.findById(conn, companyId);
            if (existing == null) {
                return OperationResult.fail("Company not found in MySQL.");
            }
            int drvCount = companyDao.countDrivesForCompany(conn, existing.getId());
            if (drvCount > 0) {
                return OperationResult.fail("Cannot delete \"" + existing.getName() + "\": "
                        + drvCount + " placement drive(s) exist for this company.");
            }
            conn.setAutoCommit(false);
            try {
                companyDao.deleteCompany(conn, existing.getId());
                auditLogDao.insertLog(conn, "COMPANY_DELETED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                        "Deleted corporate partner \"" + existing.getName() + "\" (" + existing.getId() + ").");
                conn.commit();
                return OperationResult.ok("Removed \"" + existing.getName() + "\" from MySQL directory.", null);
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Cannot delete company: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // PLACEMENT DRIVE MANAGEMENT
    // -------------------------------------------------------------------------

    public OperationResult<String> createDrive(User actor, PlacementDrive draft) {
        if (actor == null || (!actor.getRole().isPrivilegedRole() && actor.getRole() != UserRole.COMPANY)) {
            return OperationResult.fail("Only Placement Administrators or Authorized Company Recruiters may create drives.");
        }
        if (draft == null || isBlank(draft.getCompanyId()) || isBlank(draft.getJobRole())) {
            return OperationResult.fail("Company and Job Role Title are required.");
        }
        if (draft.getEligibleBranches() == null || draft.getEligibleBranches().isEmpty()) {
            return OperationResult.fail("Select at least one eligible engineering branch.");
        }
        if (actor.getRole() == UserRole.COMPANY) {
            if (isBlank(actor.getCompanyId()) || !actor.getCompanyId().equals(draft.getCompanyId())) {
                return OperationResult.fail("Company accounts may only publish placement drives for their own organization.");
            }
        }

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Company company = companyDao.findById(conn, draft.getCompanyId());
            if (company == null) {
                return OperationResult.fail("Selected partner company not found in MySQL.");
            }
            if ("REJECTED".equalsIgnoreCase(company.getApprovalStatus())) {
                return OperationResult.fail("Cannot create drives for a rejected company account.");
            }

            String driveId = "drv-" + UUID.randomUUID();
            draft.setId(driveId);
            draft.setCompanyName(company.getName());
            draft.setJobRole(draft.getJobRole().trim());

            conn.setAutoCommit(false);
            try {
                driveDao.insertDrive(conn, draft);
                auditLogDao.insertLog(conn, "DRIVE_CREATED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                        "Created placement drive: " + company.getName() + " - " + draft.getJobRole() + ".");
                conn.commit();
                return OperationResult.ok("Published placement drive for " + company.getName() + " (" + draft.getJobRole() + ") in MySQL.", driveId);
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to create placement drive: " + ex.getMessage());
        }
    }

    public OperationResult<Void> updateDrive(User actor, PlacementDrive updated) {
        if (actor == null || (!actor.getRole().isPrivilegedRole() && actor.getRole() != UserRole.COMPANY)) {
            return OperationResult.fail("Only authorized staff or company recruiters may update placement drives.");
        }
        if (updated == null || isBlank(updated.getId()) || isBlank(updated.getJobRole())) {
            return OperationResult.fail("Drive ID and Job Role are required.");
        }
        if (updated.getEligibleBranches() == null || updated.getEligibleBranches().isEmpty()) {
            return OperationResult.fail("Select at least one eligible engineering branch.");
        }

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            PlacementDrive existing = driveDao.findById(conn, updated.getId());
            if (existing == null) {
                return OperationResult.fail("Placement drive not found in MySQL.");
            }

            if (actor.getRole() == UserRole.COMPANY) {
                if (isBlank(actor.getCompanyId()) || !actor.getCompanyId().equals(existing.getCompanyId())) {
                    return OperationResult.fail("Company accounts can only edit their own company's placement drives.");
                }
                updated.setCompanyId(existing.getCompanyId());
            }

            Company company = companyDao.findById(conn, updated.getCompanyId());
            if (company == null) {
                return OperationResult.fail("Selected company not found in MySQL.");
            }
            updated.setCompanyName(company.getName());
            updated.setJobRole(updated.getJobRole().trim());

            conn.setAutoCommit(false);
            try {
                driveDao.updateDrive(conn, updated);
                auditLogDao.insertLog(conn, "DRIVE_UPDATED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                        "Updated placement drive " + updated.getId() + " (" + company.getName() + " - " + updated.getJobRole() + ").");
                conn.commit();
                return OperationResult.ok("Updated MySQL placement drive: " + updated.getJobRole() + ".", null);
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to update placement drive: " + ex.getMessage());
        }
    }

    public OperationResult<Void> deleteDrive(User actor, String driveId) {
        if (actor == null || !actor.getRole().isPrivilegedRole()) {
            return OperationResult.fail("Only Placement Officers or College Administrators may delete placement drives.");
        }
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            PlacementDrive existing = driveDao.findById(conn, driveId);
            if (existing == null) {
                return OperationResult.fail("Placement drive not found in MySQL.");
            }
            int appCount = driveDao.countApplicationsForDrive(conn, existing.getId());
            if (appCount > 0) {
                return OperationResult.fail("Cannot delete placement drive \"" + existing.getJobRole()
                        + "\": " + appCount + " student application(s) exist for this drive.");
            }
            conn.setAutoCommit(false);
            try {
                driveDao.deleteDriveCascade(conn, existing.getId());
                auditLogDao.insertLog(conn, "DRIVE_DELETED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                        "Deleted placement drive " + existing.getCompanyName() + " - " + existing.getJobRole() + ".");
                conn.commit();
                return OperationResult.ok("Removed drive \"" + existing.getJobRole() + "\" from MySQL.", null);
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Cannot delete drive: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // ELIGIBILITY-GATED APPLICATIONS & 10-STAGE RECRUITMENT PIPELINE
    // -------------------------------------------------------------------------

    public OperationResult<String> submitApplication(User actor, String driveId, String studentId) {
        if (actor == null) {
            return OperationResult.fail("Authentication required.");
        }
        if (actor.getRole() == UserRole.COMPANY || actor.getRole() == UserRole.FACULTY) {
            return OperationResult.fail("Only students (or placement officers on behalf of eligible students) may submit drive applications.");
        }
        if (isBlank(driveId) || isBlank(studentId)) {
            return OperationResult.fail("Drive and Student selections are required.");
        }

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Student student = studentDao.findByIdOrRoll(conn, studentId);
            if (student == null) {
                return OperationResult.fail("Student record not found in MySQL.");
            }

            if (actor.getRole() == UserRole.STUDENT) {
                if (isBlank(actor.getStudentRollNumber())
                        || !actor.getStudentRollNumber().equalsIgnoreCase(student.getRollNumber())) {
                    return OperationResult.fail("Students may only apply using their own student profile.");
                }
            }

            PlacementDrive drive = driveDao.findById(conn, driveId);
            if (drive == null) {
                return OperationResult.fail("Placement drive not found in MySQL.");
            }
            if (!PlacementEngine.isDriveOpenForApplications(drive)) {
                return OperationResult.fail("Applications are closed for this drive (Current Status: " + drive.getStatus() + ").");
            }

            // Enforce live academic & skill eligibility before saving application
            List<Skill> allSkills = skillDao.findAllSkills(conn);
            EligibilityResult elig = PlacementEngine.checkEligibility(student, drive, allSkills);
            if (!elig.eligible) {
                String summary = !elig.reasons.isEmpty() ? elig.reasons.get(0) : "Eligibility criteria not satisfied.";
                return OperationResult.fail("Ineligible to apply: " + summary);
            }

            Application existing = applicationDao.findByStudentAndDrive(conn, student.getId(), drive.getId());
            if (existing != null && existing.getCurrentStage() != ApplicationStage.WITHDRAWN) {
                return OperationResult.fail("Student has already applied to this placement drive (Stage: "
                        + existing.getCurrentStage().getLabel() + ").");
            }

            String appId = existing != null ? existing.getId() : "app-" + UUID.randomUUID();
            conn.setAutoCommit(false);
            try {
                if (existing != null) {
                    applicationDao.reapplyWithdrawnApplication(conn, appId, actor.getFullName());
                } else {
                    applicationDao.insertApplicationWithHistory(
                            conn, appId, drive.getId(), student.getId(),
                            drive.getCompanyName(), drive.getJobRole(), actor.getFullName());
                }
                auditLogDao.insertLog(conn, "APPLICATION_SUBMITTED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                        student.getFullName() + " (" + student.getRollNumber() + ") applied to "
                                + drive.getCompanyName() + " (" + drive.getJobRole() + ").");
                conn.commit();
                return OperationResult.ok(
                        (existing != null ? "Re-submitted" : "Submitted") + " application for "
                                + drive.getCompanyName() + " (" + drive.getJobRole() + ") in MySQL.",
                        appId);
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to submit application: " + ex.getMessage());
        }
    }

    public OperationResult<Void> advanceApplicationStage(
            User actor,
            String appId,
            ApplicationStage nextStage,
            Double finalOfferLpa,
            String rejectionReason,
            String notes
    ) {
        if (actor == null) {
            return OperationResult.fail("Authentication required.");
        }
        if (actor.getRole() == UserRole.STUDENT || actor.getRole() == UserRole.FACULTY) {
            return OperationResult.fail("Only Placement Officers, Administrators, or authorized Company Recruiters can advance stages.");
        }
        if (isBlank(appId) || nextStage == null) {
            return OperationResult.fail("Application ID and Target Stage are required.");
        }
        if (nextStage == ApplicationStage.REJECTED && isBlank(rejectionReason)) {
            return OperationResult.fail("Please enter a specific technical or interview rejection reason.");
        }

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Application existing = applicationDao.findById(conn, appId);
            if (existing == null) {
                return OperationResult.fail("Application not found in MySQL.");
            }
            if (actor.getRole() == UserRole.COMPANY) {
                if (isBlank(actor.getCompanyId()) || !actor.getCompanyId().equals(existing.getCompanyId())) {
                    return OperationResult.fail("Companies can only manage applicants for their own placement drives.");
                }
            }
            if (existing.getCurrentStage() == ApplicationStage.WITHDRAWN) {
                return OperationResult.fail("Cannot advance a withdrawn application unless the student re-applies.");
            }

            Double resolvedOffer = finalOfferLpa;
            if (resolvedOffer == null && nextStage.isOfferOrSelected()) {
                PlacementDrive drive = driveDao.findById(conn, existing.getDriveId());
                if (drive != null && drive.getSalaryPackageLpa() > 0) {
                    resolvedOffer = drive.getSalaryPackageLpa();
                }
            }

            conn.setAutoCommit(false);
            try {
                applicationDao.updateApplicationStage(
                        conn,
                        existing.getId(),
                        existing.getStudentId(),
                        existing.getCompanyId(),
                        nextStage,
                        resolvedOffer,
                        rejectionReason,
                        notes,
                        actor.getFullName()
                );
                auditLogDao.insertLog(conn, "APPLICATION_STAGE_UPDATED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                        "Application " + existing.getId() + " (" + existing.getStudentName() + " -> "
                                + existing.getCompanyName() + ") moved to " + nextStage.name() + ".");
                conn.commit();
                return OperationResult.ok(existing.getStudentName() + " updated to \"" + nextStage.getLabel() + "\" in MySQL.", null);
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Stage update failed: " + ex.getMessage());
        }
    }

    public OperationResult<Void> withdrawApplication(User actor, String appId) {
        if (actor == null || isBlank(appId)) {
            return OperationResult.fail("Invalid withdrawal request.");
        }
        if (actor.getRole() != UserRole.STUDENT && !actor.getRole().isPrivilegedRole()) {
            return OperationResult.fail("Not authorized to withdraw this application.");
        }

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Application existing = applicationDao.findById(conn, appId);
            if (existing == null) {
                return OperationResult.fail("Application not found in MySQL.");
            }
            if (actor.getRole() == UserRole.STUDENT) {
                if (isBlank(actor.getStudentRollNumber())
                        || !actor.getStudentRollNumber().equalsIgnoreCase(existing.getStudentRollNumber())) {
                    return OperationResult.fail("Students may only withdraw their own applications.");
                }
            }
            if (existing.getCurrentStage() == ApplicationStage.WITHDRAWN) {
                return OperationResult.fail("Application is already withdrawn.");
            }
            if (existing.getCurrentStage().isOfferOrSelected()) {
                return OperationResult.fail("Selected/Offered/Joined applications cannot be withdrawn directly.");
            }

            conn.setAutoCommit(false);
            try {
                applicationDao.withdrawApplication(conn, existing.getId(), actor.getFullName());
                auditLogDao.insertLog(conn, "APPLICATION_WITHDRAWN", actor.getId(), actor.getUsername(), actor.getRole().name(),
                        "Application " + existing.getId() + " (" + existing.getCompanyName() + ") withdrawn.");
                conn.commit();
                return OperationResult.ok("Withdrew application for " + existing.getCompanyName() + " in MySQL.", null);
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Withdrawal failed: " + ex.getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
