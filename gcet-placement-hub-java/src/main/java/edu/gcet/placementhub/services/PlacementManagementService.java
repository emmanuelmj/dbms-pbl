package edu.gcet.placementhub.services;

import com.mongodb.MongoException;
import com.mongodb.client.MongoDatabase;
import edu.gcet.placementhub.dao.*;
import edu.gcet.placementhub.database.DatabaseConnection;
import edu.gcet.placementhub.database.DatabaseException;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.models.PlacementModels.EligibilityResult;
import edu.gcet.placementhub.models.PlacementModels.PlacementBundle;
import edu.gcet.placementhub.utils.OperationResult;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * MongoDB-backed placement service managing Companies, Placement Drives,
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
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            List<Company> companies = companyDao.findAll(db);
            List<PlacementDrive> drives = driveDao.findAll(db);
            List<Application> applications = applicationDao.findAllForUser(db, actor);
            List<Student> students = studentDao.findAll(db);
            List<Skill> skills = skillDao.findAllSkills(db);
            return new PlacementBundle(companies, drives, students, skills, applications, null);
        } catch (DatabaseException | MongoException ex) {
            return new PlacementBundle(List.of(), List.of(), List.of(), List.of(), List.of(),
                    "MongoDB query failed: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // CORPORATE PARTNERS / COMPANIES
    // -------------------------------------------------------------------------

    public OperationResult<String> createCompany(User actor, Company draft) {
        if (actor == null || (!actor.getRole().isPrivilegedRole() && actor.getRole() != UserRole.COMPANY)) {
            return OperationResult.fail("Only Placement Officers, Administrators, or verified Corporate Recruiters may register companies.");
        }
        if (draft == null || isBlank(draft.getName()) || isBlank(draft.getHrEmail())) {
            return OperationResult.fail("Company Name and HR Email are required.");
        }

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            if (companyDao.existsByName(db, draft.getName(), null)) {
                return OperationResult.fail("Company \"" + draft.getName().trim() + "\" already exists.");
            }

            String approvalStatus = actor.getRole().isPrivilegedRole() ? "APPROVED" : "PENDING_APPROVAL";
            draft.setApprovalStatus(approvalStatus);
            String id = companyDao.insertCompany(db, draft, actor.getId(), actor.getFullName());

            auditLogDao.insertLog(db, "COMPANY_CREATED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                    "Registered corporate partner " + draft.getName() + " (Status: " + approvalStatus + ").");
            return OperationResult.ok("Added " + draft.getName() + " to MongoDB.", id);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to create company: " + ex.getMessage());
        }
    }

    public OperationResult<Void> updateCompany(User actor, Company updated) {
        if (actor == null) return OperationResult.fail("Authentication required.");
        if (updated == null || isBlank(updated.getId()) || isBlank(updated.getName())) {
            return OperationResult.fail("Company ID and Company Name are required.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Company existing = companyDao.findById(db, updated.getId());
            if (existing == null) {
                return OperationResult.fail("Company not found in MongoDB.");
            }

            if (actor.getRole() == UserRole.COMPANY) {
                if (isBlank(actor.getCompanyId()) || !actor.getCompanyId().equals(existing.getId())) {
                    return OperationResult.fail("You are only permitted to update your own corporate profile.");
                }
                // Corporate self-updates preserve existing approval status & tier
                updated.setApprovalStatus(existing.getApprovalStatus());
                updated.setTier(existing.getTier());
            }

            if (companyDao.existsByName(db, updated.getName(), existing.getId())) {
                return OperationResult.fail("Another company named \"" + updated.getName().trim() + "\" already exists.");
            }

            companyDao.updateCompany(db, updated);
            auditLogDao.insertLog(db, "COMPANY_UPDATED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                    "Updated company details for " + updated.getName() + ".");
            return OperationResult.ok("Updated company details in MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to update company: " + ex.getMessage());
        }
    }

    public OperationResult<Void> updateCompanyApprovalStatus(User actor, String companyId, String approvalStatus, String reason) {
        if (actor == null || !actor.getRole().isAdminRole()) {
            return OperationResult.fail("Only College Administrators can approve or reject corporate partners.");
        }
        if (isBlank(companyId) || isBlank(approvalStatus)) {
            return OperationResult.fail("Company ID and status are required.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Company comp = companyDao.findById(db, companyId);
            if (comp == null) {
                return OperationResult.fail("Company not found in MongoDB.");
            }

            companyDao.updateApprovalStatus(db, companyId, approvalStatus, reason, actor.getId(), actor.getFullName());
            auditLogDao.insertLog(db, "COMPANY_APPROVAL_" + approvalStatus.toUpperCase(Locale.ROOT),
                    actor.getId(), actor.getUsername(), actor.getRole().name(),
                    "Corporate profile for " + comp.getName() + " set to " + approvalStatus + " by " + actor.getFullName());
            return OperationResult.ok("Company profile status updated to " + approvalStatus + ".", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to update approval status: " + ex.getMessage());
        }
    }

    public OperationResult<Void> deleteCompany(User actor, String companyId) {
        if (actor == null || !actor.getRole().isPrivilegedRole()) {
            return OperationResult.fail("Only Placement Officers or College Administrators may delete corporate records.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Company existing = companyDao.findById(db, companyId);
            if (existing == null) {
                return OperationResult.fail("Company not found in MongoDB.");
            }

            int driveCount = companyDao.countDrivesForCompany(db, companyId);
            if (driveCount > 0) {
                return OperationResult.fail("Cannot delete " + existing.getName()
                        + ": " + driveCount + " placement drive(s) are associated with this company.");
            }

            companyDao.deleteCompany(db, companyId);
            auditLogDao.insertLog(db, "COMPANY_DELETED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                    "Deleted corporate partner " + existing.getName() + " from MongoDB.");
            return OperationResult.ok("Deleted " + existing.getName() + " from MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Cannot delete company: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // ON-CAMPUS & POOL PLACEMENT DRIVES
    // -------------------------------------------------------------------------

    public OperationResult<String> createDrive(User actor, PlacementDrive draft) {
        if (actor == null) {
            return OperationResult.fail("Authentication required.");
        }
        if (actor.getRole() == UserRole.STUDENT || actor.getRole() == UserRole.FACULTY) {
            return OperationResult.fail("Only Placement Officers, Administrators, or registered Companies may schedule drives.");
        }
        if (draft == null || isBlank(draft.getJobRole()) || isBlank(draft.getCompanyId())) {
            return OperationResult.fail("Company selection and Job Role are required.");
        }

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Company comp = companyDao.findById(db, draft.getCompanyId());
            if (comp == null) {
                return OperationResult.fail("Target company not found in MongoDB.");
            }
            if (actor.getRole() == UserRole.COMPANY) {
                if (isBlank(actor.getCompanyId()) || !actor.getCompanyId().equals(comp.getId())) {
                    return OperationResult.fail("Companies may only publish drives for their own corporate profile.");
                }
            }

            draft.setCompanyName(comp.getName());
            draft.setCompanyTier(comp.getTier());
            if (isBlank(draft.getStatus())) draft.setStatus("OPEN");
            if (draft.getPositions() <= 0) draft.setPositions(10);
            if (draft.getSalaryPackageLpa() <= 0) draft.setSalaryPackageLpa(4.5);
            if (draft.getMinCgpa() <= 0) draft.setMinCgpa(6.0);
            if (draft.getTargetGradYear() <= 0) draft.setTargetGradYear(2026);

            String id = driveDao.insertDrive(db, draft);
            auditLogDao.insertLog(db, "DRIVE_CREATED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                    "Created placement drive for " + draft.getCompanyName() + " (" + draft.getJobRole() + ").");
            return OperationResult.ok("Published placement drive for " + draft.getCompanyName() + " in MongoDB.", id);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to create placement drive: " + ex.getMessage());
        }
    }

    public OperationResult<Void> updateDrive(User actor, PlacementDrive updated) {
        if (actor == null) return OperationResult.fail("Authentication required.");
        if (actor.getRole() == UserRole.STUDENT || actor.getRole() == UserRole.FACULTY) {
            return OperationResult.fail("Not authorized to update placement drives.");
        }
        if (updated == null || isBlank(updated.getId()) || isBlank(updated.getJobRole())) {
            return OperationResult.fail("Drive ID and Job Role are required.");
        }

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            PlacementDrive existing = driveDao.findById(db, updated.getId());
            if (existing == null) {
                return OperationResult.fail("Placement drive not found in MongoDB.");
            }
            if (actor.getRole() == UserRole.COMPANY) {
                if (isBlank(actor.getCompanyId()) || !actor.getCompanyId().equals(existing.getCompanyId())) {
                    return OperationResult.fail("You may only modify placement drives posted by your company.");
                }
            }

            updated.setCompanyId(existing.getCompanyId());
            updated.setCompanyName(existing.getCompanyName());
            updated.setCompanyTier(existing.getCompanyTier());
            driveDao.updateDrive(db, updated);
            auditLogDao.insertLog(db, "DRIVE_UPDATED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                    "Updated drive " + existing.getCompanyName() + " (" + updated.getJobRole() + ").");
            return OperationResult.ok("Updated placement drive in MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to update placement drive: " + ex.getMessage());
        }
    }

    public OperationResult<Void> deleteDrive(User actor, String driveId) {
        if (actor == null || (!actor.getRole().isPrivilegedRole() && actor.getRole() != UserRole.COMPANY)) {
            return OperationResult.fail("Only Placement Officers, Admins, or owning Recruiters may cancel drives.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            PlacementDrive existing = driveDao.findById(db, driveId);
            if (existing == null) {
                return OperationResult.fail("Drive not found in MongoDB.");
            }
            if (actor.getRole() == UserRole.COMPANY) {
                if (isBlank(actor.getCompanyId()) || !actor.getCompanyId().equals(existing.getCompanyId())) {
                    return OperationResult.fail("Recruiters may only cancel their own company drives.");
                }
            }

            int appCount = driveDao.countApplicationsForDrive(db, driveId);
            if (appCount > 0 && !actor.getRole().isAdminRole()) {
                return OperationResult.fail("Cannot delete drive: " + appCount
                        + " student application(s) exist. Close the drive status instead or contact the administrator.");
            }

            driveDao.deleteDriveCascade(db, driveId);
            auditLogDao.insertLog(db, "DRIVE_DELETED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                    "Deleted drive " + existing.getCompanyName() + " (" + existing.getJobRole() + ") and related applications.");
            return OperationResult.ok("Deleted placement drive from MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
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

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Student student = studentDao.findByIdOrRoll(db, studentId);
            if (student == null) {
                return OperationResult.fail("Student record not found in MongoDB.");
            }

            if (actor.getRole() == UserRole.STUDENT) {
                if (isBlank(actor.getStudentRollNumber())
                        || !actor.getStudentRollNumber().equalsIgnoreCase(student.getRollNumber())) {
                    return OperationResult.fail("Students may only apply using their own student profile.");
                }
            }

            PlacementDrive drive = driveDao.findById(db, driveId);
            if (drive == null) {
                return OperationResult.fail("Placement drive not found in MongoDB.");
            }
            if (!PlacementEngine.isDriveOpenForApplications(drive)) {
                return OperationResult.fail("Applications are closed for this drive (Current Status: " + drive.getStatus() + ").");
            }

            // Enforce live academic & skill eligibility before saving application
            List<Skill> allSkills = skillDao.findAllSkills(db);
            EligibilityResult elig = PlacementEngine.checkEligibility(student, drive, allSkills);
            if (!elig.eligible) {
                String summary = !elig.reasons.isEmpty() ? elig.reasons.get(0) : "Eligibility criteria not satisfied.";
                return OperationResult.fail("Ineligible to apply: " + summary);
            }

            Application existing = applicationDao.findByStudentAndDrive(db, student.getId(), drive.getId());
            if (existing != null && existing.getCurrentStage() != ApplicationStage.WITHDRAWN) {
                return OperationResult.fail("Student has already applied to this placement drive (Stage: "
                        + existing.getCurrentStage().getLabel() + ").");
            }

            String appId = existing != null ? existing.getId() : null;
            if (existing != null) {
                applicationDao.reapplyWithdrawnApplication(db, appId, actor.getFullName());
            } else {
                appId = applicationDao.insertApplicationWithHistory(
                        db, appId, drive.getId(), student.getId(),
                        drive.getCompanyName(), drive.getJobRole(), actor.getFullName());
            }
            auditLogDao.insertLog(db, "APPLICATION_SUBMITTED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                    student.getFullName() + " (" + student.getRollNumber() + ") applied to "
                            + drive.getCompanyName() + " (" + drive.getJobRole() + ").");
            return OperationResult.ok(
                    (existing != null ? "Re-submitted" : "Submitted") + " application for "
                            + drive.getCompanyName() + " (" + drive.getJobRole() + ") in MongoDB.",
                    appId);
        } catch (DatabaseException | MongoException ex) {
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

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Application existing = applicationDao.findById(db, appId);
            if (existing == null) {
                return OperationResult.fail("Application not found in MongoDB.");
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
                PlacementDrive drive = driveDao.findById(db, existing.getDriveId());
                if (drive != null && drive.getSalaryPackageLpa() > 0) {
                    resolvedOffer = drive.getSalaryPackageLpa();
                }
            }

            applicationDao.updateApplicationStage(
                    db,
                    existing.getId(),
                    existing.getStudentId(),
                    existing.getCompanyId(),
                    nextStage,
                    resolvedOffer,
                    rejectionReason,
                    notes,
                    actor.getFullName()
            );

            auditLogDao.insertLog(db, "APPLICATION_STAGE_CHANGED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                    "Moved application " + existing.getId() + " (" + existing.getCompanyName() + ") to "
                            + nextStage.getLabel() + (resolvedOffer != null ? " [Offer: " + resolvedOffer + " LPA]" : ""));
            return OperationResult.ok("Application stage advanced to " + nextStage.getLabel() + " in MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to advance application stage: " + ex.getMessage());
        }
    }

    public OperationResult<Void> withdrawApplication(User actor, String appId) {
        if (actor == null) return OperationResult.fail("Authentication required.");
        if (isBlank(appId)) return OperationResult.fail("Application ID is required.");
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Application app = applicationDao.findById(db, appId);
            if (app == null) {
                return OperationResult.fail("Application not found in MongoDB.");
            }

            if (actor.getRole() == UserRole.STUDENT) {
                Student self = studentDao.findByIdOrRoll(db, actor.getStudentRollNumber());
                if (self == null || !self.getId().equals(app.getStudentId())) {
                    return OperationResult.fail("You are only permitted to withdraw your own applications.");
                }
            }

            applicationDao.withdrawApplication(db, appId, actor.getFullName());
            auditLogDao.insertLog(db, "APPLICATION_WITHDRAWN", actor.getId(), actor.getUsername(), actor.getRole().name(),
                    "Application " + appId + " withdrawn by " + actor.getFullName());
            return OperationResult.ok("Application withdrawn in MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to withdraw application: " + ex.getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
