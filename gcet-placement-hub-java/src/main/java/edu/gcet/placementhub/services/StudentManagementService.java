package edu.gcet.placementhub.services;

import com.mongodb.MongoException;
import com.mongodb.client.MongoDatabase;
import edu.gcet.placementhub.dao.ApplicationDao;
import edu.gcet.placementhub.dao.AuditLogDao;
import edu.gcet.placementhub.dao.FacultyDao;
import edu.gcet.placementhub.dao.SkillDao;
import edu.gcet.placementhub.dao.StudentDao;
import edu.gcet.placementhub.database.DatabaseConnection;
import edu.gcet.placementhub.database.DatabaseException;
import edu.gcet.placementhub.models.Application;
import edu.gcet.placementhub.models.Faculty;
import edu.gcet.placementhub.models.Skill;
import edu.gcet.placementhub.models.Student;
import edu.gcet.placementhub.models.StudentCertification;
import edu.gcet.placementhub.models.StudentInternship;
import edu.gcet.placementhub.models.StudentProject;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.models.UserRole;
import edu.gcet.placementhub.utils.DeptNormalizer;
import edu.gcet.placementhub.utils.OperationResult;
import edu.gcet.placementhub.utils.PasswordHasher;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * MongoDB-backed business logic for Student Directory, Individual Profiles,
 * Technical Skills Matrix, Projects, Certifications, Internships, and Faculty Directory.
 * Enforces RBAC and faculty department authorization rules.
 */
public class StudentManagementService {

    private final StudentDao studentDao = new StudentDao();
    private final SkillDao skillDao = new SkillDao();
    private final FacultyDao facultyDao = new FacultyDao();
    private final ApplicationDao applicationDao = new ApplicationDao();
    private final AuditLogDao auditLogDao = new AuditLogDao();

    public static class DirectoryBundle {
        public final List<Student> students;
        public final List<Skill> skills;
        public final List<Application> applications;
        public final String error;

        public DirectoryBundle(List<Student> students, List<Skill> skills, List<Application> applications, String error) {
            this.students = students;
            this.skills = skills;
            this.applications = applications;
            this.error = error;
        }
    }

    public DirectoryBundle loadDirectoryBundle(User currentUser) {
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            List<Student> students;
            if (currentUser != null && currentUser.getRole() == UserRole.STUDENT && currentUser.getStudentRollNumber() != null) {
                Student single = studentDao.findByIdOrRoll(db, currentUser.getStudentRollNumber());
                students = single != null ? List.of(single) : List.of();
            } else {
                students = studentDao.findAll(db);
            }
            List<Skill> skills = skillDao.findAllSkills(db);
            List<Application> applications = applicationDao.findAllForUser(db, currentUser);
            return new DirectoryBundle(students, skills, applications, null);
        } catch (DatabaseException | MongoException ex) {
            return new DirectoryBundle(List.of(), List.of(), List.of(), "MongoDB query failed: " + ex.getMessage());
        }
    }

    public OperationResult<List<Faculty>> loadFacultyDirectory(User actor) {
        if (actor == null) {
            return OperationResult.fail("Authentication required.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            List<Faculty> list = facultyDao.findAll(db);
            return OperationResult.ok("Loaded faculty directory from MongoDB.", list);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to load faculty directory: " + ex.getMessage());
        }
    }

    public OperationResult<String> createStudent(User actor, Student draft, String plainPassword) {
        if (actor == null || (!actor.getRole().isPrivilegedRole() && actor.getRole() != UserRole.FACULTY)) {
            return OperationResult.fail("Only faculty coordinators or placement administration may enroll students directly.");
        }
        if (draft == null || isBlank(draft.getRollNumber()) || isBlank(draft.getFullName())
                || isBlank(draft.getEmail()) || isBlank(draft.getBranch())) {
            return OperationResult.fail("Roll Number, Full Name, Email, and Branch are required.");
        }

        String cleanRoll = draft.getRollNumber().trim().toUpperCase(Locale.ROOT);
        String cleanEmail = draft.getEmail().trim().toLowerCase(Locale.ROOT);
        int gradYear = draft.getGraduationYear() > 0 ? draft.getGraduationYear() : 2026;
        String tail = cleanRoll.length() >= 4 ? cleanRoll.substring(cleanRoll.length() - 4) : cleanRoll;

        draft.setRollNumber(cleanRoll);
        draft.setEmail(cleanEmail);
        draft.setFullName(draft.getFullName().trim());
        draft.setBranch(draft.getBranch().trim());
        draft.setGraduationYear(gradYear);
        draft.setAcademicYear(draft.getAcademicYear() > 0 ? draft.getAcademicYear() : 4);
        draft.setYearOfStudy(draft.getYearOfStudy() > 0 ? draft.getYearOfStudy() : 4);
        draft.setStudentIdCard("GCET-" + gradYear + "-" + tail);
        if (isBlank(draft.getPlacementStatus())) {
            draft.setPlacementStatus("UNPLACED");
        }

        String rawPw = !isBlank(plainPassword) ? plainPassword : "student123";
        String[] hashPair = PasswordHasher.hashWithBCrypt(rawPw);

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            if (studentDao.existsByRollOrEmail(db, cleanRoll, cleanEmail)) {
                return OperationResult.fail("A student with roll number " + cleanRoll + " or email " + cleanEmail + " already exists.");
            }
            studentDao.insertStudentWithUser(db, draft, hashPair[0], hashPair[1]);
            auditLogDao.insertLog(
                    db,
                    "STUDENT_CREATED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Added student " + draft.getFullName() + " (" + cleanRoll + ") in MongoDB."
            );
            return OperationResult.ok("Enrolled " + draft.getFullName() + " (" + cleanRoll + ") into MongoDB.", draft.getId());
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("MongoDB enrollment failed: " + ex.getMessage());
        }
    }

    public OperationResult<Void> updateStudent(User actor, Student updated) {
        if (actor == null) return OperationResult.fail("Authentication required.");
        if (actor.getRole() == UserRole.COMPANY) {
            return OperationResult.fail("Recruiters cannot modify student records.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Student existing = studentDao.findByIdOrRoll(db, updated.getId());
            if (existing == null) {
                return OperationResult.fail("Student not found in MongoDB.");
            }
            boolean isSelfStudent = actor.getRole() == UserRole.STUDENT;
            if (isSelfStudent && (actor.getStudentRollNumber() == null
                    || !actor.getStudentRollNumber().equalsIgnoreCase(existing.getRollNumber()))) {
                return OperationResult.fail("Students may only update their own profile.");
            }

            updated.setId(existing.getId());
            studentDao.updateStudent(db, updated, isSelfStudent);
            auditLogDao.insertLog(
                    db,
                    "STUDENT_UPDATED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Updated student record " + existing.getFullName() + " (" + existing.getRollNumber() + ")."
            );
            return OperationResult.ok("Updated profile for " + existing.getFullName() + " in MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to update student: " + ex.getMessage());
        }
    }

    public OperationResult<Void> deleteStudent(User actor, String studentId) {
        if (actor == null || !actor.getRole().isPrivilegedRole()) {
            return OperationResult.fail("Only authorized placement administrators may delete student records.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Student existing = studentDao.findByIdOrRoll(db, studentId);
            if (existing == null) {
                return OperationResult.fail("Student not found in MongoDB.");
            }
            int appCount = studentDao.countApplicationsForStudent(db, existing.getId());
            if (appCount > 0) {
                return OperationResult.fail("Cannot delete " + existing.getFullName()
                        + ": " + appCount + " placement application record(s) reference this student.");
            }

            studentDao.deleteStudentCascade(db, existing.getId(), existing.getRollNumber());
            auditLogDao.insertLog(
                    db,
                    "STUDENT_DELETED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Removed student " + existing.getFullName() + " (" + existing.getRollNumber() + ") from MongoDB."
            );
            return OperationResult.ok("Deleted " + existing.getFullName() + " from MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Delete failed: " + ex.getMessage());
        }
    }

    public OperationResult<Void> upsertStudentSkill(
            User actor,
            String studentId,
            String skillId,
            int proficiency,
            boolean markVerified
    ) {
        if (actor == null) return OperationResult.fail("Authentication required.");
        if (actor.getRole() == UserRole.COMPANY) {
            return OperationResult.fail("Recruiters cannot modify student skills.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Student student = studentDao.findByIdOrRoll(db, studentId);
            if (student == null) return OperationResult.fail("Student not found.");

            if (actor.getRole() == UserRole.STUDENT
                    && (actor.getStudentRollNumber() == null
                    || !actor.getStudentRollNumber().equalsIgnoreCase(student.getRollNumber()))) {
                return OperationResult.fail("Students may only update their own skill profile.");
            }
            if (markVerified && actor.getRole() == UserRole.FACULTY
                    && !DeptNormalizer.isFacultyAuthorizedForStudent(actor.getAuthorizedDepartments(), actor.getUsername(), student.getBranch())) {
                return OperationResult.fail("Unauthorized Department: You are not assigned to verify skills for branch \"" + student.getBranch() + "\".");
            }

            studentDao.upsertStudentSkill(db, student.getId(), skillId, proficiency);
            if (markVerified && actor.getRole() != UserRole.STUDENT) {
                studentDao.verifyStudentSkill(db, student.getId(), skillId, actor.getId());
            }
            auditLogDao.insertLog(
                    db,
                    "STUDENT_SKILL_UPDATED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Updated skill " + skillId + " (proficiency " + proficiency + ") for " + student.getRollNumber() + "."
            );
            return OperationResult.ok("Saved skill proficiency (Level " + proficiency + ") in MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to update skill: " + ex.getMessage());
        }
    }

    public OperationResult<Void> verifyStudentSkill(User actor, String studentId, String skillId) {
        if (actor == null || actor.getRole() == UserRole.STUDENT || actor.getRole() == UserRole.COMPANY) {
            return OperationResult.fail("Only faculty or placement officers can verify student skills.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Student student = studentDao.findByIdOrRoll(db, studentId);
            if (student == null) return OperationResult.fail("Student not found.");

            if (actor.getRole() == UserRole.FACULTY
                    && !DeptNormalizer.isFacultyAuthorizedForStudent(actor.getAuthorizedDepartments(), actor.getUsername(), student.getBranch())) {
                return OperationResult.fail("Unauthorized Department: You are not assigned to verify skills for branch \"" + student.getBranch() + "\".");
            }

            studentDao.verifyStudentSkill(db, student.getId(), skillId, actor.getId());
            auditLogDao.insertLog(
                    db,
                    "SKILL_VERIFIED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Verified skill " + skillId + " for " + student.getFullName() + " (" + student.getRollNumber() + ")."
            );
            return OperationResult.ok("Skill verified by faculty and persisted in MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Verification failed: " + ex.getMessage());
        }
    }

    public OperationResult<Void> addProject(User actor, String studentId, StudentProject project) {
        if (actor == null || actor.getRole() == UserRole.COMPANY) {
            return OperationResult.fail("Not authorized to add student projects.");
        }
        if (project == null || isBlank(project.getTitle())) {
            return OperationResult.fail("Project title is required.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Student student = studentDao.findByIdOrRoll(db, studentId);
            if (student == null) return OperationResult.fail("Student not found.");

            if (actor.getRole() == UserRole.STUDENT
                    && (actor.getStudentRollNumber() == null
                    || !actor.getStudentRollNumber().equalsIgnoreCase(student.getRollNumber()))) {
                return OperationResult.fail("You may only add projects to your own profile.");
            }

            project.setStudentId(student.getId());
            project.setTitle(project.getTitle().trim());
            studentDao.insertProject(db, project);
            auditLogDao.insertLog(
                    db,
                    "PROJECT_ADDED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Added project \"" + project.getTitle() + "\" for " + student.getRollNumber() + "."
            );
            return OperationResult.ok("Added project \"" + project.getTitle() + "\" to MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to add project: " + ex.getMessage());
        }
    }

    public OperationResult<Void> deleteProject(User actor, String studentId, String projectId) {
        if (!canEditPortfolio(actor, studentId)) {
            return OperationResult.fail("Not authorized to remove projects from this profile.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Student student = studentDao.findByIdOrRoll(db, studentId);
            if (student == null) return OperationResult.fail("Student not found.");
            if (!canAccessSpecificStudent(actor, student)) {
                return OperationResult.fail("You may only modify your own portfolio.");
            }
            studentDao.deleteProject(db, projectId, student.getId());
            return OperationResult.ok("Project removed from MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to remove project: " + ex.getMessage());
        }
    }

    public OperationResult<Void> addCertification(User actor, String studentId, StudentCertification cert) {
        if (actor == null || actor.getRole() == UserRole.COMPANY) {
            return OperationResult.fail("Not authorized to add student certifications.");
        }
        if (cert == null || isBlank(cert.getName()) || isBlank(cert.getIssuingOrg())) {
            return OperationResult.fail("Certification name and issuing organization are required.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Student student = studentDao.findByIdOrRoll(db, studentId);
            if (student == null) return OperationResult.fail("Student not found.");
            if (!canAccessSpecificStudent(actor, student)) {
                return OperationResult.fail("You may only add certifications to your own profile.");
            }

            cert.setStudentId(student.getId());
            cert.setName(cert.getName().trim());
            cert.setIssuingOrg(cert.getIssuingOrg().trim());
            if (isBlank(cert.getIssueDate())) {
                cert.setIssueDate(LocalDate.now().toString());
            } else {
                cert.setIssueDate(cert.getIssueDate().trim());
            }
            studentDao.insertCertification(db, cert);
            auditLogDao.insertLog(
                    db,
                    "CERTIFICATION_ADDED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Added certification \"" + cert.getName() + "\" for " + student.getRollNumber() + "."
            );
            return OperationResult.ok("Added certification \"" + cert.getName() + "\" to MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to add certification: " + ex.getMessage());
        }
    }

    public OperationResult<Void> deleteCertification(User actor, String studentId, String certId) {
        if (!canEditPortfolio(actor, studentId)) {
            return OperationResult.fail("Not authorized to remove certifications.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Student student = studentDao.findByIdOrRoll(db, studentId);
            if (student == null) return OperationResult.fail("Student not found.");
            if (!canAccessSpecificStudent(actor, student)) {
                return OperationResult.fail("You may only modify your own portfolio.");
            }
            studentDao.deleteCertification(db, certId, student.getId());
            return OperationResult.ok("Certification removed from MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to remove certification: " + ex.getMessage());
        }
    }

    public OperationResult<Void> addInternship(User actor, String studentId, StudentInternship internship) {
        if (actor == null || actor.getRole() == UserRole.COMPANY) {
            return OperationResult.fail("Not authorized to add student internships.");
        }
        if (internship == null || isBlank(internship.getCompanyName()) || isBlank(internship.getRole()) || isBlank(internship.getDuration())) {
            return OperationResult.fail("Company Name, Role, and Duration are required.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Student student = studentDao.findByIdOrRoll(db, studentId);
            if (student == null) return OperationResult.fail("Student not found.");
            if (!canAccessSpecificStudent(actor, student)) {
                return OperationResult.fail("You may only add internships to your own profile.");
            }

            internship.setStudentId(student.getId());
            internship.setCompanyName(internship.getCompanyName().trim());
            internship.setRole(internship.getRole().trim());
            internship.setDuration(internship.getDuration().trim());
            studentDao.insertInternship(db, internship);
            auditLogDao.insertLog(
                    db,
                    "INTERNSHIP_ADDED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Added internship at " + internship.getCompanyName() + " for " + student.getRollNumber() + "."
            );
            return OperationResult.ok("Added internship at " + internship.getCompanyName() + " to MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to add internship: " + ex.getMessage());
        }
    }

    public OperationResult<Void> deleteInternship(User actor, String studentId, String internshipId) {
        if (!canEditPortfolio(actor, studentId)) {
            return OperationResult.fail("Not authorized to remove internships.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Student student = studentDao.findByIdOrRoll(db, studentId);
            if (student == null) return OperationResult.fail("Student not found.");
            if (!canAccessSpecificStudent(actor, student)) {
                return OperationResult.fail("You may only modify your own portfolio.");
            }
            studentDao.deleteInternship(db, internshipId, student.getId());
            return OperationResult.ok("Internship removed from MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to remove internship: " + ex.getMessage());
        }
    }

    public OperationResult<String> createSkill(User actor, Skill skill) {
        if (!canManageSkillCatalog(actor)) {
            return OperationResult.fail("Only faculty coordinators or placement officers can add skills.");
        }
        if (skill == null || isBlank(skill.getName()) || isBlank(skill.getCategory())) {
            return OperationResult.fail("Skill name and category are required.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            if (skillDao.existsByName(db, skill.getName())) {
                return OperationResult.fail("Skill \"" + skill.getName().trim() + "\" already exists.");
            }
            skill.setName(skill.getName().trim());
            skill.setCategory(skill.getCategory().trim());
            String id = skillDao.insertSkill(db, skill);
            auditLogDao.insertLog(
                    db,
                    "SKILL_CREATED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Added competency \"" + skill.getName() + "\" to Skill Matrix."
            );
            return OperationResult.ok("Added " + skill.getName() + " to MongoDB skills catalog.", id);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to create skill: " + ex.getMessage());
        }
    }

    public OperationResult<Void> updateSkill(User actor, Skill skill) {
        if (!canManageSkillCatalog(actor)) {
            return OperationResult.fail("Only faculty coordinators or placement officers can update skills.");
        }
        if (skill == null || isBlank(skill.getId()) || isBlank(skill.getName())) {
            return OperationResult.fail("Skill ID and name are required.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            skill.setName(skill.getName().trim());
            skillDao.updateSkill(db, skill);
            auditLogDao.insertLog(
                    db,
                    "SKILL_UPDATED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Updated skill " + skill.getName() + " (" + skill.getId() + ")."
            );
            return OperationResult.ok("Saved changes to " + skill.getName() + " in MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to update skill: " + ex.getMessage());
        }
    }

    public OperationResult<Void> deleteSkill(User actor, Skill skill) {
        if (!canManageSkillCatalog(actor)) {
            return OperationResult.fail("Only faculty coordinators or placement officers can delete skills.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            int ssCount = skillDao.countStudentReferences(db, skill.getId());
            int drCount = skillDao.countDriveReferences(db, skill.getId());
            if (ssCount > 0 || drCount > 0) {
                return OperationResult.fail("Cannot delete skill: referenced by " + ssCount
                        + " student skill(s) and " + drCount + " placement drive(s).");
            }
            skillDao.deleteSkill(db, skill.getId());
            auditLogDao.insertLog(
                    db,
                    "SKILL_DELETED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Deleted skill " + skill.getName() + " (" + skill.getId() + ")."
            );
            return OperationResult.ok("Removed " + skill.getName() + " from MongoDB skills catalog.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Cannot delete skill: " + ex.getMessage());
        }
    }

    private boolean canManageSkillCatalog(User actor) {
        return actor != null && (actor.getRole().isPrivilegedRole() || actor.getRole() == UserRole.FACULTY);
    }

    private boolean canEditPortfolio(User actor, String studentId) {
        return actor != null && actor.getRole() != UserRole.COMPANY && !isBlank(studentId);
    }

    private boolean canAccessSpecificStudent(User actor, Student student) {
        if (actor.getRole() != UserRole.STUDENT) return true;
        return actor.getStudentRollNumber() != null
                && actor.getStudentRollNumber().equalsIgnoreCase(student.getRollNumber());
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
