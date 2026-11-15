package edu.gcet.placementhub.services;

import edu.gcet.placementhub.dao.*;
import edu.gcet.placementhub.database.DatabaseConnection;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.utils.DeptNormalizer;
import edu.gcet.placementhub.utils.PasswordHasher;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * Pure JDBC Phase 3 business logic for Student Directory, Individual Profiles,
 * Technical Skills Matrix, Projects, Certifications, Internships, and Faculty Directory.
 * Enforces exact RBAC and faculty department authorization rules from server/routes.ts.
 */
public class StudentManagementService {

    private final StudentDao studentDao = new StudentDao();
    private final SkillDao skillDao = new SkillDao();
    private final FacultyDao facultyDao = new FacultyDao();
    private final ApplicationDao applicationDao = new ApplicationDao();
    private final AuditLogDao auditLogDao = new AuditLogDao();

    public static class OperationResult<T> {
        public final boolean success;
        public final String message;
        public final T data;

        public OperationResult(boolean success, String message, T data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public static <T> OperationResult<T> ok(String msg, T data) {
            return new OperationResult<>(true, msg, data);
        }

        public static <T> OperationResult<T> fail(String msg) {
            return new OperationResult<>(false, msg, null);
        }
    }

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
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            List<Student> students;
            if (currentUser != null && currentUser.getRole() == UserRole.STUDENT && currentUser.getStudentRollNumber() != null) {
                Student single = studentDao.findByIdOrRoll(conn, currentUser.getStudentRollNumber());
                students = single != null ? List.of(single) : List.of();
            } else {
                students = studentDao.findAll(conn);
            }
            List<Skill> skills = skillDao.findAllSkills(conn);
            List<Application> applications = applicationDao.findAllForUser(conn, currentUser);
            return new DirectoryBundle(students, skills, applications, null);
        } catch (SQLException ex) {
            return new DirectoryBundle(List.of(), List.of(), List.of(), "MySQL query failed: " + ex.getMessage());
        }
    }

    public OperationResult<List<Faculty>> loadFacultyDirectory(User actor) {
        if (actor == null) {
            return OperationResult.fail("Authentication required.");
        }
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            List<Faculty> list = facultyDao.findAll(conn);
            return OperationResult.ok("Loaded faculty directory from MySQL.", list);
        } catch (SQLException ex) {
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
        String studentId = "stu-" + UUID.randomUUID();
        String userId = "usr-" + UUID.randomUUID();
        int gradYear = draft.getGraduationYear() > 0 ? draft.getGraduationYear() : 2026;
        String tail = cleanRoll.length() >= 4 ? cleanRoll.substring(cleanRoll.length() - 4) : cleanRoll;

        draft.setId(studentId);
        draft.setUserId(userId);
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

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            if (studentDao.existsByRollOrEmail(conn, cleanRoll, cleanEmail)) {
                return OperationResult.fail("A student with roll number " + cleanRoll + " or email " + cleanEmail + " already exists.");
            }
            conn.setAutoCommit(false);
            try {
                studentDao.insertStudentWithUser(conn, draft, hashPair[0], hashPair[1]);
                auditLogDao.insertLog(
                        conn,
                        "STUDENT_CREATED",
                        actor.getId(),
                        actor.getUsername(),
                        actor.getRole().name(),
                        "Added student " + draft.getFullName() + " (" + cleanRoll + ") in MySQL."
                );
                conn.commit();
                return OperationResult.ok("Enrolled " + draft.getFullName() + " (" + cleanRoll + ") into MySQL.", studentId);
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("MySQL enrollment failed: " + ex.getMessage());
        }
    }

    public OperationResult<Void> updateStudent(User actor, Student updated) {
        if (actor == null) return OperationResult.fail("Authentication required.");
        if (actor.getRole() == UserRole.COMPANY) {
            return OperationResult.fail("Recruiters cannot modify student records.");
        }
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Student existing = studentDao.findByIdOrRoll(conn, updated.getId());
            if (existing == null) {
                return OperationResult.fail("Student not found in MySQL.");
            }
            boolean isSelfStudent = actor.getRole() == UserRole.STUDENT;
            if (isSelfStudent && (actor.getStudentRollNumber() == null
                    || !actor.getStudentRollNumber().equalsIgnoreCase(existing.getRollNumber()))) {
                return OperationResult.fail("Students may only update their own profile.");
            }

            updated.setId(existing.getId());
            studentDao.updateStudent(conn, updated, isSelfStudent);
            auditLogDao.insertLog(
                    conn,
                    "STUDENT_UPDATED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Updated student record " + existing.getFullName() + " (" + existing.getRollNumber() + ")."
            );
            return OperationResult.ok("Updated profile for " + existing.getFullName() + " in MySQL.", null);
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to update student: " + ex.getMessage());
        }
    }

    public OperationResult<Void> deleteStudent(User actor, String studentId) {
        if (actor == null || !actor.getRole().isPrivilegedRole()) {
            return OperationResult.fail("Only authorized placement administrators may delete student records.");
        }
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Student existing = studentDao.findByIdOrRoll(conn, studentId);
            if (existing == null) {
                return OperationResult.fail("Student not found in MySQL.");
            }
            int appCount = studentDao.countApplicationsForStudent(conn, existing.getId());
            if (appCount > 0) {
                return OperationResult.fail("Cannot delete " + existing.getFullName()
                        + ": " + appCount + " placement application record(s) reference this student.");
            }

            conn.setAutoCommit(false);
            try {
                studentDao.deleteStudentCascade(conn, existing.getId(), existing.getRollNumber());
                auditLogDao.insertLog(
                        conn,
                        "STUDENT_DELETED",
                        actor.getId(),
                        actor.getUsername(),
                        actor.getRole().name(),
                        "Removed student " + existing.getFullName() + " (" + existing.getRollNumber() + ") from MySQL."
                );
                conn.commit();
                return OperationResult.ok("Deleted " + existing.getFullName() + " from MySQL.", null);
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
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
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Student student = studentDao.findByIdOrRoll(conn, studentId);
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

            boolean canVerify = actor.getRole() != UserRole.STUDENT && markVerified;
            studentDao.upsertStudentSkill(conn, student.getId(), skillId, proficiency, canVerify, actor.getId());
            auditLogDao.insertLog(
                    conn,
                    "STUDENT_SKILL_UPDATED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Updated skill " + skillId + " (proficiency " + proficiency + ") for " + student.getRollNumber() + "."
            );
            return OperationResult.ok("Saved skill proficiency (Level " + proficiency + ") in MySQL.", null);
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to update skill: " + ex.getMessage());
        }
    }

    public OperationResult<Void> verifyStudentSkill(User actor, String studentId, String skillId) {
        if (actor == null || actor.getRole() == UserRole.STUDENT || actor.getRole() == UserRole.COMPANY) {
            return OperationResult.fail("Only faculty or placement officers can verify student skills.");
        }
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Student student = studentDao.findByIdOrRoll(conn, studentId);
            if (student == null) return OperationResult.fail("Student not found.");

            if (actor.getRole() == UserRole.FACULTY
                    && !DeptNormalizer.isFacultyAuthorizedForStudent(actor.getAuthorizedDepartments(), actor.getUsername(), student.getBranch())) {
                return OperationResult.fail("Unauthorized Department: You are not assigned to verify skills for branch \"" + student.getBranch() + "\".");
            }

            studentDao.verifyStudentSkill(conn, student.getId(), skillId, actor.getId());
            auditLogDao.insertLog(
                    conn,
                    "SKILL_VERIFIED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Verified skill " + skillId + " for " + student.getFullName() + " (" + student.getRollNumber() + ")."
            );
            return OperationResult.ok("Skill verified by faculty and persisted in MySQL.", null);
        } catch (SQLException ex) {
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
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Student student = studentDao.findByIdOrRoll(conn, studentId);
            if (student == null) return OperationResult.fail("Student not found.");

            if (actor.getRole() == UserRole.STUDENT
                    && (actor.getStudentRollNumber() == null
                    || !actor.getStudentRollNumber().equalsIgnoreCase(student.getRollNumber()))) {
                return OperationResult.fail("You may only add projects to your own profile.");
            }

            project.setId("proj-" + UUID.randomUUID());
            project.setStudentId(student.getId());
            project.setTitle(project.getTitle().trim());
            studentDao.insertProject(conn, project);
            auditLogDao.insertLog(
                    conn,
                    "PROJECT_ADDED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Added project \"" + project.getTitle() + "\" for " + student.getRollNumber() + "."
            );
            return OperationResult.ok("Added project \"" + project.getTitle() + "\" to MySQL.", null);
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to add project: " + ex.getMessage());
        }
    }

    public OperationResult<Void> deleteProject(User actor, String studentId, String projectId) {
        if (!canEditPortfolio(actor, studentId)) {
            return OperationResult.fail("Not authorized to remove projects from this profile.");
        }
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Student student = studentDao.findByIdOrRoll(conn, studentId);
            if (student == null) return OperationResult.fail("Student not found.");
            if (!canAccessSpecificStudent(actor, student)) {
                return OperationResult.fail("You may only modify your own portfolio.");
            }
            studentDao.deleteProject(conn, projectId, student.getId());
            return OperationResult.ok("Project removed from MySQL.", null);
        } catch (SQLException ex) {
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
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Student student = studentDao.findByIdOrRoll(conn, studentId);
            if (student == null) return OperationResult.fail("Student not found.");
            if (!canAccessSpecificStudent(actor, student)) {
                return OperationResult.fail("You may only add certifications to your own profile.");
            }

            cert.setId("cert-" + UUID.randomUUID());
            cert.setStudentId(student.getId());
            cert.setName(cert.getName().trim());
            cert.setIssuingOrg(cert.getIssuingOrg().trim());
            if (isBlank(cert.getIssueDate())) {
                cert.setIssueDate(LocalDate.now().toString());
            } else {
                cert.setIssueDate(cert.getIssueDate().trim());
            }
            studentDao.insertCertification(conn, cert);
            auditLogDao.insertLog(
                    conn,
                    "CERTIFICATION_ADDED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Added certification \"" + cert.getName() + "\" for " + student.getRollNumber() + "."
            );
            return OperationResult.ok("Added certification \"" + cert.getName() + "\" to MySQL.", null);
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to add certification: " + ex.getMessage());
        }
    }

    public OperationResult<Void> deleteCertification(User actor, String studentId, String certId) {
        if (!canEditPortfolio(actor, studentId)) {
            return OperationResult.fail("Not authorized to remove certifications.");
        }
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Student student = studentDao.findByIdOrRoll(conn, studentId);
            if (student == null) return OperationResult.fail("Student not found.");
            if (!canAccessSpecificStudent(actor, student)) {
                return OperationResult.fail("You may only modify your own portfolio.");
            }
            studentDao.deleteCertification(conn, certId, student.getId());
            return OperationResult.ok("Certification removed from MySQL.", null);
        } catch (SQLException ex) {
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
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Student student = studentDao.findByIdOrRoll(conn, studentId);
            if (student == null) return OperationResult.fail("Student not found.");
            if (!canAccessSpecificStudent(actor, student)) {
                return OperationResult.fail("You may only add internships to your own profile.");
            }

            internship.setId("int-" + UUID.randomUUID());
            internship.setStudentId(student.getId());
            internship.setCompanyName(internship.getCompanyName().trim());
            internship.setRole(internship.getRole().trim());
            internship.setDuration(internship.getDuration().trim());
            studentDao.insertInternship(conn, internship);
            auditLogDao.insertLog(
                    conn,
                    "INTERNSHIP_ADDED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Added internship at " + internship.getCompanyName() + " for " + student.getRollNumber() + "."
            );
            return OperationResult.ok("Added internship at " + internship.getCompanyName() + " to MySQL.", null);
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to add internship: " + ex.getMessage());
        }
    }

    public OperationResult<Void> deleteInternship(User actor, String studentId, String internshipId) {
        if (!canEditPortfolio(actor, studentId)) {
            return OperationResult.fail("Not authorized to remove internships.");
        }
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Student student = studentDao.findByIdOrRoll(conn, studentId);
            if (student == null) return OperationResult.fail("Student not found.");
            if (!canAccessSpecificStudent(actor, student)) {
                return OperationResult.fail("You may only modify your own portfolio.");
            }
            studentDao.deleteInternship(conn, internshipId, student.getId());
            return OperationResult.ok("Internship removed from MySQL.", null);
        } catch (SQLException ex) {
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
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            if (skillDao.existsByName(conn, skill.getName())) {
                return OperationResult.fail("Skill \"" + skill.getName().trim() + "\" already exists.");
            }
            String id = "sk-" + UUID.randomUUID();
            skill.setId(id);
            skill.setName(skill.getName().trim());
            skill.setCategory(skill.getCategory().trim());
            skillDao.insertSkill(conn, skill);
            auditLogDao.insertLog(
                    conn,
                    "SKILL_CREATED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Added competency \"" + skill.getName() + "\" to Skill Matrix."
            );
            return OperationResult.ok("Added " + skill.getName() + " to MySQL skills catalog.", id);
        } catch (SQLException ex) {
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
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            skill.setName(skill.getName().trim());
            skillDao.updateSkill(conn, skill);
            auditLogDao.insertLog(
                    conn,
                    "SKILL_UPDATED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Updated skill " + skill.getName() + " (" + skill.getId() + ")."
            );
            return OperationResult.ok("Saved changes to " + skill.getName() + " in MySQL.", null);
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to update skill: " + ex.getMessage());
        }
    }

    public OperationResult<Void> deleteSkill(User actor, Skill skill) {
        if (!canManageSkillCatalog(actor)) {
            return OperationResult.fail("Only faculty coordinators or placement officers can delete skills.");
        }
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            int ssCount = skillDao.countStudentReferences(conn, skill.getId());
            int drCount = skillDao.countDriveReferences(conn, skill.getId());
            if (ssCount > 0 || drCount > 0) {
                return OperationResult.fail("Cannot delete skill: referenced by " + ssCount
                        + " student skill(s) and " + drCount + " placement drive(s).");
            }
            skillDao.deleteSkill(conn, skill.getId());
            auditLogDao.insertLog(
                    conn,
                    "SKILL_DELETED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Deleted skill " + skill.getName() + " (" + skill.getId() + ")."
            );
            return OperationResult.ok("Removed " + skill.getName() + " from MySQL skills catalog.", null);
        } catch (SQLException ex) {
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
