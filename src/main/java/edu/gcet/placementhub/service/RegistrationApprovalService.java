package edu.gcet.placementhub.service;

import edu.gcet.placementhub.auth.PasswordUtil;
import edu.gcet.placementhub.dao.*;
import edu.gcet.placementhub.db.DatabaseConnection;
import edu.gcet.placementhub.model.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * GCET PLACEMENT HUB - Registration and Approval Workflow Service
 * Coordinates multi-role registrations (Faculty, Student, Company, Admin Request)
 * and enforces mandatory dual-approval and institutional authority policies.
 */
public class RegistrationApprovalService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserDao userDao = new UserDao();
    private final FacultyDao facultyDao = new FacultyDao();
    private final StudentDao studentDao = new StudentDao();
    private final CompanyDao companyDao = new CompanyDao();
    private final ApprovalsDao approvalsDao = new ApprovalsDao();

    // ========================================================
    // 1. New Faculty Registration & Dual Approval
    // ========================================================

    public void registerFaculty(String fullName, String employeeId, String department, String designation,
                                String email, String phone, String username, String password) throws StudentService.ValidationException, SQLException {

        validateUsernameAndEmail(username, email, null);

        if (fullName == null || fullName.trim().length() < 2) {
            throw new StudentService.ValidationException("Full Name is required (minimum 2 characters).");
        }
        if (employeeId == null || employeeId.trim().isEmpty()) {
            throw new StudentService.ValidationException("Employee ID is required (e.g. EMP-CSE-105).");
        }
        if (password == null || password.length() < 6) {
            throw new StudentService.ValidationException("Password must be at least 6 characters long.");
        }

        String userId = UUID.randomUUID().toString();
        String facultyId = "GCET-FAC-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Create User account with PENDING status
                String salt = PasswordUtil.generateSalt();
                String hash = PasswordUtil.hashPassword(password, salt);

                String userSql = "INSERT INTO users (id, username, email, full_name, role, department, password_hash, salt, status, is_active, must_change_password) " +
                                 "VALUES (?, ?, ?, ?, 'FACULTY', ?, ?, ?, 'PENDING', FALSE, FALSE)";
                try (PreparedStatement ps = conn.prepareStatement(userSql)) {
                    ps.setString(1, userId);
                    ps.setString(2, username.trim().toLowerCase());
                    ps.setString(3, email.trim().toLowerCase());
                    ps.setString(4, fullName.trim());
                    ps.setString(5, department);
                    ps.setString(6, hash);
                    ps.setString(7, salt);
                    ps.executeUpdate();
                }

                // 2. Create Faculty record with status PENDING, dual approval flags FALSE
                String facSql = "INSERT INTO faculty (id, faculty_id, employee_id, full_name, department, designation, email, phone, user_id, faculty_approved, admin_approved, status) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, FALSE, 'PENDING')";
                try (PreparedStatement ps = conn.prepareStatement(facSql)) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, facultyId);
                    ps.setString(3, employeeId.trim());
                    ps.setString(4, fullName.trim());
                    ps.setString(5, department);
                    ps.setString(6, designation);
                    ps.setString(7, email.trim().toLowerCase());
                    ps.setString(8, phone != null ? phone.trim() : null);
                    ps.setString(9, userId);
                    ps.executeUpdate();
                }

                conn.commit();
                approvalsDao.logAudit("FACULTY_REGISTRATION_SUBMITTED", userId, username, "FACULTY", "Submitted registration requiring dual approval.");
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void approveFacultyByFaculty(User currentUser, String facultyDbId) {
        PermissionService.require(PermissionService.canApproveFacultyFacultySide(currentUser), "conduct faculty-side approval");

        Faculty f = facultyDao.findById(facultyDbId);
        if (f == null) throw new IllegalArgumentException("Faculty record not found.");

        boolean ok = facultyDao.approveByFaculty(facultyDbId, currentUser.getId(), currentUser.getFullName());
        if (!ok) throw new RuntimeException("Failed to record faculty approval.");

        approvalsDao.logApprovalRecord(new ApprovalRecord(
            UUID.randomUUID().toString(),
            ApprovalRecord.RequestType.FACULTY_REGISTRATION,
            facultyDbId,
            f.getFullName(),
            currentUser.getId(),
            currentUser.getFullName(),
            currentUser.getRole().name(),
            "APPROVED",
            "Approved on behalf of departmental faculty review panel."
        ));
    }

    public void approveFacultyByAdmin(User currentUser, String facultyDbId) {
        PermissionService.require(PermissionService.canApproveFacultyAdminSide(currentUser), "conduct administrative approval for faculty");

        Faculty f = facultyDao.findById(facultyDbId);
        if (f == null) throw new IllegalArgumentException("Faculty record not found.");

        boolean ok = facultyDao.approveByAdmin(facultyDbId, currentUser.getId(), currentUser.getFullName());
        if (!ok) throw new RuntimeException("Failed to record administrator approval.");

        approvalsDao.logApprovalRecord(new ApprovalRecord(
            UUID.randomUUID().toString(),
            ApprovalRecord.RequestType.FACULTY_REGISTRATION,
            facultyDbId,
            f.getFullName(),
            currentUser.getId(),
            currentUser.getFullName(),
            currentUser.getRole().name(),
            "APPROVED",
            "Approved on behalf of college administration."
        ));
    }

    public void rejectFaculty(User currentUser, String facultyDbId, String reason) {
        boolean canReject = PermissionService.canApproveFacultyAdminSide(currentUser) || PermissionService.canApproveFacultyFacultySide(currentUser);
        PermissionService.require(canReject, "reject faculty registration");

        Faculty f = facultyDao.findById(facultyDbId);
        if (f == null) throw new IllegalArgumentException("Faculty record not found.");

        boolean ok = facultyDao.rejectFaculty(facultyDbId, currentUser.getId(), currentUser.getFullName(), reason);
        if (!ok) throw new RuntimeException("Failed to record faculty rejection.");

        approvalsDao.logApprovalRecord(new ApprovalRecord(
            UUID.randomUUID().toString(),
            ApprovalRecord.RequestType.FACULTY_REGISTRATION,
            facultyDbId,
            f.getFullName(),
            currentUser.getId(),
            currentUser.getFullName(),
            currentUser.getRole().name(),
            "REJECTED",
            reason
        ));
    }

    // ========================================================
    // 2. Student Registration & Faculty Approval
    // ========================================================

    public void registerStudent(String fullName, String rollNumber, String studentIdCard, String branch,
                                int yearOfStudy, String section, String email, String phone,
                                String username, String password) throws StudentService.ValidationException, SQLException {

        validateUsernameAndEmail(username, email, null);

        if (rollNumber == null || rollNumber.trim().isEmpty()) {
            throw new StudentService.ValidationException("Roll Number is required (e.g. 21R11A0501).");
        }
        String roll = rollNumber.trim().toUpperCase();
        if (studentDao.isRollNumberTaken(roll, null)) {
            throw new StudentService.ValidationException("Roll Number '" + roll + "' is already registered in the system.");
        }

        if (studentIdCard != null && !studentIdCard.trim().isEmpty()) {
            if (studentDao.isStudentIdCardTaken(studentIdCard.trim(), null)) {
                throw new StudentService.ValidationException("Student ID Card '" + studentIdCard.trim() + "' is already in use.");
            }
        }

        if (password == null || password.length() < 6) {
            throw new StudentService.ValidationException("Password must be at least 6 characters long.");
        }

        String userId = UUID.randomUUID().toString();
        String studentId = UUID.randomUUID().toString();

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Create User account with PENDING status
                String salt = PasswordUtil.generateSalt();
                String hash = PasswordUtil.hashPassword(password, salt);

                String userSql = "INSERT INTO users (id, username, email, full_name, role, department, student_roll_number, password_hash, salt, status, is_active, must_change_password) " +
                                 "VALUES (?, ?, ?, ?, 'STUDENT', ?, ?, ?, ?, 'PENDING', FALSE, FALSE)";
                try (PreparedStatement ps = conn.prepareStatement(userSql)) {
                    ps.setString(1, userId);
                    ps.setString(2, username.trim().toLowerCase());
                    ps.setString(3, email.trim().toLowerCase());
                    ps.setString(4, fullName.trim());
                    ps.setString(5, branch);
                    ps.setString(6, roll);
                    ps.setString(7, hash);
                    ps.setString(8, salt);
                    ps.executeUpdate();
                }

                // 2. Create Student profile with approval_status PENDING
                String stuSql = "INSERT INTO students (id, roll_number, student_id_card, full_name, email, phone, branch, year_of_study, section, user_id, approval_status, cgpa, tenth_percentage, inter_percentage, graduation_year, backlogs, placement_status) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0.0, 0.0, 0.0, ?, 0, 'UNPLACED')";
                int gradYear = 2026 + (4 - Math.min(4, Math.max(1, yearOfStudy)));

                try (PreparedStatement ps = conn.prepareStatement(stuSql)) {
                    ps.setString(1, studentId);
                    ps.setString(2, roll);
                    ps.setString(3, studentIdCard != null ? studentIdCard.trim() : null);
                    ps.setString(4, fullName.trim());
                    ps.setString(5, email.trim().toLowerCase());
                    ps.setString(6, phone != null ? phone.trim() : null);
                    ps.setString(7, branch);
                    ps.setInt(8, yearOfStudy);
                    ps.setString(9, section != null ? section.trim() : "A");
                    ps.setString(10, userId);
                    ps.setInt(11, gradYear);
                    ps.executeUpdate();
                }

                conn.commit();
                approvalsDao.logAudit("STUDENT_REGISTRATION_SUBMITTED", userId, username, "STUDENT", "Submitted student registration (" + roll + ").");
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void approveStudent(User currentUser, String studentDbId) throws SQLException {
        PermissionService.require(PermissionService.canApproveStudentRegistration(currentUser), "approve student registrations");

        boolean ok = studentDao.approveStudent(studentDbId, currentUser.getId(), currentUser.getFullName());
        if (!ok) throw new RuntimeException("Failed to approve student record.");

        approvalsDao.logApprovalRecord(new ApprovalRecord(
            UUID.randomUUID().toString(),
            ApprovalRecord.RequestType.STUDENT_REGISTRATION,
            studentDbId,
            "Student ID: " + studentDbId,
            currentUser.getId(),
            currentUser.getFullName(),
            currentUser.getRole().name(),
            "APPROVED",
            "Approved student academic registration."
        ));
    }

    public void rejectStudent(User currentUser, String studentDbId, String reason) throws SQLException {
        PermissionService.require(PermissionService.canApproveStudentRegistration(currentUser), "reject student registrations");

        boolean ok = studentDao.rejectStudent(studentDbId, currentUser.getId(), currentUser.getFullName(), reason);
        if (!ok) throw new RuntimeException("Failed to reject student record.");

        approvalsDao.logApprovalRecord(new ApprovalRecord(
            UUID.randomUUID().toString(),
            ApprovalRecord.RequestType.STUDENT_REGISTRATION,
            studentDbId,
            "Student ID: " + studentDbId,
            currentUser.getId(),
            currentUser.getFullName(),
            currentUser.getRole().name(),
            "REJECTED",
            reason
        ));
    }

    // ========================================================
    // 3. Company Registration & Admin Approval
    // ========================================================

    public void registerCompany(String companyName, String industry, String website, String location,
                                String hrName, String hrEmail, String hrPhone, String description,
                                String username, String password) throws StudentService.ValidationException, SQLException {

        validateUsernameAndEmail(username, hrEmail, null);

        if (companyName == null || companyName.trim().length() < 2) {
            throw new StudentService.ValidationException("Company Name is required.");
        }
        if (companyDao.isCompanyNameTaken(companyName, null)) {
            throw new StudentService.ValidationException("Company '" + companyName + "' is already registered on GCET Placement Hub.");
        }
        if (password == null || password.length() < 6) {
            throw new StudentService.ValidationException("Password must be at least 6 characters long.");
        }

        String userId = UUID.randomUUID().toString();
        String companyId = UUID.randomUUID().toString();

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Create User account with role COMPANY and PENDING status
                String salt = PasswordUtil.generateSalt();
                String hash = PasswordUtil.hashPassword(password, salt);

                String userSql = "INSERT INTO users (id, username, email, full_name, role, company_id, password_hash, salt, status, is_active, must_change_password) " +
                                 "VALUES (?, ?, ?, ?, 'COMPANY', ?, ?, ?, 'PENDING', FALSE, FALSE)";
                try (PreparedStatement ps = conn.prepareStatement(userSql)) {
                    ps.setString(1, userId);
                    ps.setString(2, username.trim().toLowerCase());
                    ps.setString(3, hrEmail.trim().toLowerCase());
                    ps.setString(4, companyName.trim() + " (" + hrName + ")");
                    ps.setString(5, companyId);
                    ps.setString(6, hash);
                    ps.setString(7, salt);
                    ps.executeUpdate();
                }

                // 2. Create Company profile with PENDING approval
                Company c = new Company();
                c.setId(companyId);
                c.setName(companyName.trim());
                c.setIndustry(industry != null ? industry.trim() : "Information Technology");
                c.setWebsite(website != null ? website.trim() : "");
                c.setLocation(location != null ? location.trim() : "Hyderabad");
                c.setDescription(description != null ? description.trim() : "");
                c.setHrName(hrName.trim());
                c.setHrEmail(hrEmail.trim().toLowerCase());
                c.setHrPhone(hrPhone != null ? hrPhone.trim() : "");
                c.setUserId(userId);
                c.setApprovalStatus("PENDING");

                companyDao.insert(c);

                conn.commit();
                approvalsDao.logAudit("COMPANY_REGISTRATION_SUBMITTED", userId, username, "COMPANY", "Submitted corporate recruiter registration: " + companyName);
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void approveCompany(User currentUser, String companyId) {
        PermissionService.require(PermissionService.canApproveCompanyRegistration(currentUser), "approve company registration");

        Company c = companyDao.findById(companyId);
        if (c == null) throw new IllegalArgumentException("Company record not found.");

        boolean ok = companyDao.approveCompany(companyId, currentUser.getId(), currentUser.getFullName());
        if (!ok) throw new RuntimeException("Failed to approve company record.");

        approvalsDao.logApprovalRecord(new ApprovalRecord(
            UUID.randomUUID().toString(),
            ApprovalRecord.RequestType.COMPANY_REGISTRATION,
            companyId,
            c.getName(),
            currentUser.getId(),
            currentUser.getFullName(),
            currentUser.getRole().name(),
            "APPROVED",
            "Approved corporate partner for campus placement drives."
        ));
    }

    public void rejectCompany(User currentUser, String companyId, String reason) {
        PermissionService.require(PermissionService.canApproveCompanyRegistration(currentUser), "reject company registration");

        Company c = companyDao.findById(companyId);
        if (c == null) throw new IllegalArgumentException("Company record not found.");

        boolean ok = companyDao.rejectCompany(companyId, currentUser.getId(), currentUser.getFullName(), reason);
        if (!ok) throw new RuntimeException("Failed to reject company record.");

        approvalsDao.logApprovalRecord(new ApprovalRecord(
            UUID.randomUUID().toString(),
            ApprovalRecord.RequestType.COMPANY_REGISTRATION,
            companyId,
            c.getName(),
            currentUser.getId(),
            currentUser.getFullName(),
            currentUser.getRole().name(),
            "REJECTED",
            reason
        ));
    }

    // ========================================================
    // 4. Administrator Privilege Requests (Principal Exclusive)
    // ========================================================

    public void requestAdminPrivilege(User currentUser, String requestedRole, String reason) throws StudentService.ValidationException {
        if (currentUser == null) throw new SecurityException("Authentication required.");
        if (reason == null || reason.trim().length() < 10) {
            throw new StudentService.ValidationException("Please provide a detailed justification for administrator privileges (minimum 10 characters).");
        }

        AdminRequest ar = new AdminRequest();
        ar.setUserId(currentUser.getId());
        ar.setApplicantName(currentUser.getFullName());
        ar.setApplicantEmail(currentUser.getEmail());
        ar.setCurrentRole(currentUser.getRole().name());
        ar.setRequestedRole(requestedRole != null ? requestedRole : "ADMIN");
        ar.setDepartment(currentUser.getDepartment());
        ar.setReason(reason.trim());

        boolean ok = approvalsDao.submitAdminRequest(ar);
        if (!ok) throw new RuntimeException("Failed to submit administrator privilege request.");

        approvalsDao.logAudit("ADMIN_PRIVILEGE_REQUESTED", currentUser.getId(), currentUser.getUsername(), currentUser.getRole().name(), "Submitted request to Principal Dr. K. Sagar.");
    }

    public void decideAdminRequest(User currentUser, String requestId, boolean approve, String notes) {
        // ONLY the Principal (Dr. K. Sagar) can approve or reject administrator requests!
        PermissionService.require(PermissionService.canApproveAdminRequests(currentUser), "decide administrator privilege requests (Principal Exclusive)");

        boolean ok = approvalsDao.decideAdminRequest(requestId, currentUser.getId(), currentUser.getFullName(), approve, notes);
        if (!ok) throw new RuntimeException("Failed to record decision on administrator request.");

        approvalsDao.logApprovalRecord(new ApprovalRecord(
            UUID.randomUUID().toString(),
            ApprovalRecord.RequestType.ADMIN_PRIVILEGE,
            requestId,
            "Admin Request " + requestId,
            currentUser.getId(),
            currentUser.getFullName(),
            "PRINCIPAL",
            approve ? "APPROVED" : "REJECTED",
            notes
        ));
    }

    private void validateUsernameAndEmail(String username, String email, String excludeUserId) throws StudentService.ValidationException {
        if (username == null || username.trim().length() < 3) {
            throw new StudentService.ValidationException("Username must be at least 3 characters long.");
        }
        String un = username.trim().toLowerCase();
        if (un.contains(" ")) {
            throw new StudentService.ValidationException("Username cannot contain spaces.");
        }
        if (userDao.isUsernameTaken(un, excludeUserId)) {
            throw new StudentService.ValidationException("Username '" + un + "' is already registered.");
        }

        if (email == null || email.trim().isEmpty()) {
            throw new StudentService.ValidationException("Email address is required.");
        }
        String em = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(em).matches()) {
            throw new StudentService.ValidationException("Invalid email format.");
        }
        if (userDao.isEmailTaken(em, excludeUserId)) {
            throw new StudentService.ValidationException("Email '" + em + "' is already registered in the system.");
        }
    }
}
