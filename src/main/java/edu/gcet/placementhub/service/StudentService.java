package edu.gcet.placementhub.service;

import edu.gcet.placementhub.config.AppConfig;
import edu.gcet.placementhub.dao.StudentDao;
import edu.gcet.placementhub.model.Student;
import edu.gcet.placementhub.model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * GCET PLACEMENT HUB - Student Service
 * Enforces business logic, validation rules, and Role-Based Access Control (RBAC).
 */
public class StudentService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private final StudentDao studentDao;

    public StudentService() {
        this.studentDao = new StudentDao();
    }

    public StudentService(StudentDao studentDao) {
        this.studentDao = studentDao;
    }

    public List<Student> getStudents(User user, String branchFilter, Integer gradYearFilter, String searchQuery) throws SQLException {
        // Students may only view student list if permitted or limited
        return studentDao.findWithFilters(branchFilter, gradYearFilter, searchQuery);
    }

    public Optional<Student> getStudentById(User user, String studentId) throws SQLException {
        Optional<Student> studentOpt = studentDao.findById(studentId);
        if (studentOpt.isPresent() && user.getRole() == User.Role.STUDENT) {
            Student s = studentOpt.get();
            if (user.getStudentRollNumber() != null && !user.getStudentRollNumber().equalsIgnoreCase(s.getRollNumber())) {
                throw new SecurityException("Students are only authorized to access their own student profile.");
            }
        }
        return studentOpt;
    }

    public void addStudent(User user, Student student) throws SQLException, ValidationException {
        // RBAC check
        if (!user.canManageStudents()) {
            throw new SecurityException("Permission Denied: Only Placement Officers and Administrators can register new students.");
        }

        validateStudentFields(student, null);

        studentDao.insert(student);
    }

    public void updateStudent(User user, Student student) throws SQLException, ValidationException {
        // RBAC check
        if (!user.canEditStudent(student.getRollNumber())) {
            throw new SecurityException("Permission Denied: You do not have permission to modify this student record.");
        }

        validateStudentFields(student, student.getId());

        studentDao.update(student);
    }

    public void deleteStudent(User user, String studentId) throws SQLException {
        // RBAC check
        if (!user.canManageStudents()) {
            throw new SecurityException("Permission Denied: Only Placement Officers and Administrators can delete student records.");
        }

        String blocker = studentDao.checkDeletionBlockers(studentId);
        if (blocker != null) {
            throw new IllegalStateException("Deletion Blocked: " + blocker);
        }

        studentDao.delete(studentId);
    }

    public void validateStudentFields(Student s, String existingStudentId) throws SQLException, ValidationException {
        if (s == null) {
            throw new ValidationException("Student record cannot be null.");
        }

        // 1. Roll Number Validation
        if (s.getRollNumber() == null || s.getRollNumber().trim().isEmpty()) {
            throw new ValidationException("Roll Number is required.");
        }
        String roll = s.getRollNumber().trim().toUpperCase();
        if (roll.length() < 5 || roll.length() > 20) {
            throw new ValidationException("Roll Number must be between 5 and 20 characters (e.g. 21R11A0501).");
        }
        if (studentDao.isRollNumberTaken(roll, existingStudentId)) {
            throw new ValidationException("Roll Number '" + roll + "' is already registered in the system.");
        }
        s.setRollNumber(roll);

        // 2. Full Name Validation
        if (s.getFullName() == null || s.getFullName().trim().length() < 2) {
            throw new ValidationException("Full Name is required (minimum 2 characters).");
        }

        // 3. Email Validation
        if (s.getEmail() == null || s.getEmail().trim().isEmpty()) {
            throw new ValidationException("Email address is required.");
        }
        String email = s.getEmail().trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("Invalid email format (e.g., student@gcet.edu.in).");
        }
        if (studentDao.isEmailTaken(email, existingStudentId)) {
            throw new ValidationException("Email address '" + email + "' is already registered to another student.");
        }
        s.setEmail(email);

        // 4. Branch Validation
        if (s.getBranch() == null || s.getBranch().trim().isEmpty()) {
            throw new ValidationException("Branch is required.");
        }
        if (!AppConfig.BRANCHES.contains(s.getBranch())) {
            throw new ValidationException("Branch '" + s.getBranch() + "' is not a recognized GCET Engineering branch.");
        }

        // 5. CGPA Validation
        if (s.getCgpa() < 0.0 || s.getCgpa() > 10.0) {
            throw new ValidationException("CGPA must be between 0.00 and 10.00.");
        }

        // 6. Graduation Year Validation
        if (s.getGraduationYear() < 2020 || s.getGraduationYear() > 2035) {
            throw new ValidationException("Graduation Year must be between 2020 and 2035.");
        }

        // 7. Backlogs Validation
        if (s.getBacklogs() < 0) {
            throw new ValidationException("Active backlogs count cannot be negative.");
        }
    }

    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
}
