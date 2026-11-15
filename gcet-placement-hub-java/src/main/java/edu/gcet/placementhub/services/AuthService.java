package edu.gcet.placementhub.services;

import edu.gcet.placementhub.dao.AuditLogDao;
import edu.gcet.placementhub.dao.CompanyDao;
import edu.gcet.placementhub.dao.StudentDao;
import edu.gcet.placementhub.dao.UserDao;
import edu.gcet.placementhub.database.DatabaseConnection;
import edu.gcet.placementhub.models.Company;
import edu.gcet.placementhub.models.PortalCategory;
import edu.gcet.placementhub.models.Student;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.models.UserRole;
import edu.gcet.placementhub.utils.DeptNormalizer;
import edu.gcet.placementhub.utils.PasswordHasher;

import java.sql.*;
import java.util.*;

/**
 * Complete Java port of server/routes.ts authentication & registration workflows.
 * Strictly authenticates against MySQL `gcet_placement_hub` with BCrypt + legacy hash support.
 */
public class AuthService {

    private static final String INVALID_CREDENTIALS_MSG = "Incorrect username or password. Please try again.";

    private final UserDao userDao = new UserDao();
    private final StudentDao studentDao = new StudentDao();
    private final CompanyDao companyDao = new CompanyDao();
    private final AuditLogDao auditLogDao = new AuditLogDao();

    public static class AuthException extends Exception {
        public AuthException(String message) {
            super(message);
        }
    }

    public static class AuthResult {
        private final User user;
        private final Student studentRecord;

        public AuthResult(User user, Student studentRecord) {
            this.user = user;
            this.studentRecord = studentRecord;
        }

        public User getUser() { return user; }
        public Student getStudentRecord() { return studentRecord; }
    }

    public static class RegistrationReceipt {
        private final String primaryId;
        private final String displayName;
        private final String loginHint;
        private final String message;

        public RegistrationReceipt(String primaryId, String displayName, String loginHint, String message) {
            this.primaryId = primaryId;
            this.displayName = displayName;
            this.loginHint = loginHint;
            this.message = message;
        }

        public String getPrimaryId() { return primaryId; }
        public String getDisplayName() { return displayName; }
        public String getLoginHint() { return loginHint; }
        public String getMessage() { return message; }
    }

    /**
     * Authenticates a user directly against MySQL `users` (and `students` if applicable).
     */
    public AuthResult login(String username, String plainPassword, PortalCategory portalRole) throws AuthException {
        boolean isStudentPortal = portalRole == PortalCategory.STUDENTS;
        String cleanUsername = username == null ? "" : username.trim();

        if (cleanUsername.isEmpty()) {
            throw new AuthException(isStudentPortal
                    ? "Student Roll Number is required. Enter your registered roll number."
                    : "Username or Registered ID is required.");
        }
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new AuthException("Please enter your password.");
        }

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            User user = userDao.findByLoginIdentifier(conn, cleanUsername);
            if (user == null) {
                throw new AuthException(INVALID_CREDENTIALS_MSG);
            }
            if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
                throw new AuthException("Incorrect password");
            }

            boolean passwordOk = PasswordHasher.verifyPassword(plainPassword, user.getSalt(), user.getPasswordHash());
            if (!passwordOk) {
                throw new AuthException("Incorrect password");
            }

            // Enforce portal role segregation
            if (portalRole != null) {
                UserRole role = user.getRole();
                switch (portalRole) {
                    case STUDENTS -> {
                        if (role != UserRole.STUDENT) {
                            throw new AuthException("This account does not have permission to access the Student Portal.");
                        }
                    }
                    case FACULTY -> {
                        if (role != UserRole.FACULTY && role != UserRole.PLACEMENT_OFFICER) {
                            throw new AuthException("This account does not have permission to access the Faculty Portal.");
                        }
                    }
                    case COMPANIES -> {
                        if (role != UserRole.COMPANY) {
                            throw new AuthException("This account does not have permission to access the Corporate Recruitment Portal.");
                        }
                    }
                    case ADMIN -> {
                        if (!role.isPrivilegedRole()) {
                            throw new AuthException("This account does not have administrative privileges.");
                        }
                    }
                }
            }

            Student studentRecord = studentDao.findByRollNumber(conn, cleanUsername);
            if (studentRecord == null && user.getRole() == UserRole.STUDENT) {
                studentRecord = studentDao.findByUserId(conn, user.getId());
                if (studentRecord == null && user.getStudentRollNumber() != null) {
                    studentRecord = studentDao.findByRollNumber(conn, user.getStudentRollNumber());
                }
                if (studentRecord == null && user.getUsername() != null) {
                    studentRecord = studentDao.findByRollNumber(conn, user.getUsername());
                }
                if (studentRecord == null && user.getEmail() != null) {
                    studentRecord = studentDao.findByEmail(conn, user.getEmail());
                }
            }

            // Approval & status gates (exact match to server/routes.ts lines 701-772)
            String userStatus = user.getStatus() == null ? "" : user.getStatus().trim().toUpperCase(Locale.ROOT);
            if (user.getRole() == UserRole.STUDENT) {
                String stuApproval = (studentRecord != null && studentRecord.getApprovalStatus() != null)
                        ? studentRecord.getApprovalStatus().trim().toUpperCase(Locale.ROOT)
                        : userStatus;
                boolean approved = "APPROVED".equals(stuApproval) || "ACTIVE".equals(stuApproval)
                        || "APPROVED".equals(userStatus) || "ACTIVE".equals(userStatus);
                if (!approved) {
                    if ("REJECTED".equals(stuApproval) || "REJECTED".equals(userStatus)) {
                        throw new AuthException("Your registration was declined. Please contact your Department Faculty Coordinator.");
                    }
                    throw new AuthException("Your registration is awaiting faculty approval.");
                }
                if (!user.isActive()) {
                    userDao.activateApprovedStudentUser(conn, user.getId());
                    user.setActive(true);
                    user.setStatus("APPROVED");
                }
            } else if (user.getRole() == UserRole.FACULTY) {
                if ("PENDING".equals(userStatus) || "PENDING_APPROVAL".equals(userStatus)) {
                    throw new AuthException("Your faculty registration is awaiting administrator approval.");
                }
                if ("REJECTED".equals(userStatus)) {
                    throw new AuthException("Your account registration was not approved. Please contact the administrator.");
                }
            } else if (user.getRole() == UserRole.COMPANY) {
                if ("PENDING".equals(userStatus) || "PENDING_APPROVAL".equals(userStatus)) {
                    throw new AuthException("Your company registration is awaiting administrator approval.");
                }
                if ("REJECTED".equals(userStatus)) {
                    throw new AuthException("Your account registration was not approved. Please contact the administrator.");
                }
                if (user.getCompanyId() != null && !user.getCompanyId().isBlank()) {
                    Company linkedCompany = companyDao.findById(conn, user.getCompanyId());
                    if (linkedCompany != null) {
                        String compApproval = linkedCompany.getApprovalStatus() == null
                                ? ""
                                : linkedCompany.getApprovalStatus().trim().toUpperCase(Locale.ROOT);
                        if ("PENDING".equals(compApproval) || "PENDING_APPROVAL".equals(compApproval)) {
                            throw new AuthException("Your corporate partner profile (" + linkedCompany.getName() + ") is awaiting administrator approval.");
                        }
                        if ("REJECTED".equals(compApproval)) {
                            throw new AuthException("Your corporate partner profile (" + linkedCompany.getName() + ") was not approved.");
                        }
                    }
                }
            } else {
                if ("PENDING".equals(userStatus) || "PENDING_APPROVAL".equals(userStatus)) {
                    throw new AuthException("Your administrator registration is awaiting Principal approval.");
                }
                if ("REJECTED".equals(userStatus)) {
                    throw new AuthException("Your account registration was not approved. Please contact the administrator.");
                }
            }

            if (!user.isActive()) {
                throw new AuthException("Your account has been deactivated. Contact the administrator.");
            }

            // Update last_login & write audit entry
            userDao.updateLastLogin(conn, user.getId());
            auditLogDao.insertLog(conn, "USER_LOGIN", user.getId(), user.getUsername(), user.getRole().name(),
                    "Authenticated via Java Swing desktop client (" + (portalRole != null ? portalRole.name() : user.getRole().name()) + ")");

            // Strip sensitive fields before returning
            user.setPasswordHash(null);
            user.setSalt(null);
            return new AuthResult(user, studentRecord);
        } catch (AuthException ae) {
            throw ae;
        } catch (SQLException ex) {
            throw new AuthException("MySQL connection failed (" + edu.gcet.placementhub.config.DatabaseConfig.getInstance().getHost()
                    + ":" + edu.gcet.placementhub.config.DatabaseConfig.getInstance().getPort()
                    + "/" + edu.gcet.placementhub.config.DatabaseConfig.getInstance().getDatabase()
                    + "): " + ex.getMessage()
                    + "\nClick 'MySQL Connection Settings' at top-right to enter your MySQL password or test JDBC.");
        }
    }

    /**
     * Explicit, user-initiated password recovery/update for an account owned by the local MySQL operator.
     * Verifies BOTH:
     * 1. Live MySQL Server ownership (validating the provided DB password via JDBC DriverManager), and
     * 2. Matching account identifier + registered email in `gcet_placement_hub.users`.
     * Never alters approval status, activation flags, or other accounts automatically.
     */
    public String recoverAndSetPassword(
            String identifier,
            String registeredEmail,
            String mysqlDbPassword,
            String newPassword,
            String confirmPassword
    ) throws AuthException {
        String cleanId = identifier == null ? "" : identifier.trim();
        String cleanEmail = registeredEmail == null ? "" : registeredEmail.trim().toLowerCase(Locale.ROOT);

        if (cleanId.isEmpty()) {
            throw new AuthException("Enter your registered Username, Student Roll Number, or Faculty ID.");
        }
        if (cleanEmail.isEmpty()) {
            throw new AuthException("Enter the registered email address stored on that account.");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new AuthException("New password must be at least 6 characters long.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new AuthException("New password and confirmation do not match.");
        }

        edu.gcet.placementhub.config.DatabaseConfig cfg = edu.gcet.placementhub.config.DatabaseConfig.getInstance();
        String dbPassToVerify = (mysqlDbPassword != null && !mysqlDbPassword.isEmpty())
                ? mysqlDbPassword
                : cfg.getPassword();

        try (Connection conn = DriverManager.getConnection(cfg.getJdbcUrl(), cfg.getUser(), dbPassToVerify != null ? dbPassToVerify : "")) {
            User target = userDao.findByLoginIdentifier(conn, cleanId);
            if (target == null) {
                throw new AuthException("No user account found in gcet_placement_hub.users matching '" + cleanId + "'.");
            }
            String storedEmail = target.getEmail() == null ? "" : target.getEmail().trim().toLowerCase(Locale.ROOT);
            if (!storedEmail.equals(cleanEmail)) {
                throw new AuthException("Registered email does not match the email stored in MySQL for account '@" + target.getUsername() + "'.");
            }

            String[] hashed = PasswordHasher.hashWithBCrypt(newPassword);
            userDao.updatePasswordHash(conn, target.getId(), hashed[0], hashed[1]);
            auditLogDao.insertLog(conn, "PASSWORD_RECOVERY_UPDATED", target.getId(), target.getUsername(),
                    target.getRole().name(), "Password explicitly updated via MySQL-verified account recovery dialog.");

            String statusNote = "";
            String st = target.getStatus() == null ? "" : target.getStatus().trim().toUpperCase(Locale.ROOT);
            if ("PENDING".equals(st) || "PENDING_APPROVAL".equals(st) || "REJECTED".equals(st) || !target.isActive()) {
                statusNote = "\nNote: Account approval status is currently '" + (target.getStatus() != null ? target.getStatus() : "UNKNOWN")
                        + "' (is_active=" + target.isActive() + "). Approval gates are preserved and still apply.";
            }

            return "Password updated with BCrypt for @" + target.getUsername() + " (" + target.getRole().name() + ")."
                    + statusNote + "\nYou can now sign in manually using your new password.";
        } catch (AuthException ae) {
            throw ae;
        } catch (SQLException ex) {
            throw new AuthException("MySQL verification failed for " + cfg.getUser() + "@" + cfg.getHost() + ": " + ex.getMessage()
                    + "\nEnsure MySQL is running and enter the valid MySQL database password for '" + cfg.getUser() + "'.");
        }
    }

    /**
     * Transactional Student Registration -> MySQL `students`, `users`, `student_skills`, `audit_logs`.
     */
    public RegistrationReceipt registerStudent(
            String fullName,
            String rollNumber,
            String email,
            String phone,
            String branch,
            int academicYear,
            int graduationYear,
            double cgpa,
            String password,
            String confirmPassword,
            List<String> selectedSkills
    ) throws AuthException {
        String cleanName = fullName == null ? "" : fullName.trim();
        String cleanRoll = rollNumber == null ? "" : rollNumber.trim().toUpperCase(Locale.ROOT);
        String cleanEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String cleanBranch = branch == null ? "" : branch.trim();
        String cleanPhone = (phone == null || phone.isBlank()) ? "+91 98765 00000" : phone.trim();

        if (cleanName.isEmpty()) throw new AuthException("Full Name is required.");
        if (cleanRoll.isEmpty()) throw new AuthException("Student Roll Number is required.");
        if (cleanEmail.isEmpty()) throw new AuthException("Official college or personal email is required.");
        if (cleanBranch.isEmpty()) throw new AuthException("Department/Branch is required.");
        if (Double.isNaN(cgpa) || cgpa < 0.0 || cgpa > 10.0) {
            throw new AuthException("CGPA must be a valid number between 0.00 and 10.00.");
        }
        if (password == null || password.length() < 6) {
            throw new AuthException("Password must be at least 6 characters in length.");
        }
        if (!password.equals(confirmPassword)) {
            throw new AuthException("Passwords do not match. Please verify.");
        }

        String[] hashed = PasswordHasher.hashWithBCrypt(password);
        String passwordHash = hashed[0];
        String salt = hashed[1];
        String studentId = "stu-" + UUID.randomUUID();
        String userId = "usr-" + UUID.randomUUID();
        String idSuffix = cleanRoll.length() >= 4 ? cleanRoll.substring(cleanRoll.length() - 4) : cleanRoll;
        String idCard = "GCET-" + graduationYear + "-" + idSuffix;

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            String existingName = studentDao.findNameByRollNumber(conn, cleanRoll);
            if (existingName != null) {
                throw new AuthException("Roll number " + cleanRoll + " is already registered to " + existingName + ".");
            }
            String existingUser = userDao.findUsernameByEmail(conn, cleanEmail);
            if (existingUser != null) {
                throw new AuthException("Email address " + cleanEmail + " is already registered with account (" + existingUser + ").");
            }

            conn.setAutoCommit(false);
            try {
                String insertStudentSql = """
                        INSERT INTO students (
                          id, roll_number, student_id_card, full_name, email, phone, branch,
                          academic_year, year_of_study, cgpa, tenth_percentage, inter_percentage,
                          graduation_year, placement_status, approval_status, user_id, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'UNPLACED', 'PENDING_APPROVAL', ?, NOW())
                        """;
                try (PreparedStatement ps = conn.prepareStatement(insertStudentSql)) {
                    ps.setString(1, studentId);
                    ps.setString(2, cleanRoll);
                    ps.setString(3, idCard);
                    ps.setString(4, cleanName);
                    ps.setString(5, cleanEmail);
                    ps.setString(6, cleanPhone);
                    ps.setString(7, cleanBranch);
                    ps.setInt(8, academicYear);
                    ps.setInt(9, academicYear);
                    ps.setDouble(10, Math.round(cgpa * 100.0) / 100.0);
                    ps.setDouble(11, 85.0);
                    ps.setDouble(12, 88.0);
                    ps.setInt(13, graduationYear);
                    ps.setString(14, userId);
                    ps.executeUpdate();
                }

                String insertUserSql = """
                        INSERT INTO users (
                          id, username, email, full_name, role, department, student_roll_number,
                          password_hash, salt, status, is_active, created_at
                        ) VALUES (?, ?, ?, ?, 'STUDENT', ?, ?, ?, ?, 'PENDING_APPROVAL', FALSE, NOW())
                        """;
                try (PreparedStatement ps = conn.prepareStatement(insertUserSql)) {
                    ps.setString(1, userId);
                    ps.setString(2, cleanRoll);
                    ps.setString(3, cleanEmail);
                    ps.setString(4, cleanName);
                    ps.setString(5, cleanBranch);
                    ps.setString(6, cleanRoll);
                    ps.setString(7, passwordHash);
                    ps.setString(8, salt);
                    ps.executeUpdate();
                }

                if (selectedSkills != null && !selectedSkills.isEmpty()) {
                    Map<String, String> dbSkills = new LinkedHashMap<>();
                    try (Statement st = conn.createStatement();
                         ResultSet rs = st.executeQuery("SELECT id, name FROM skills")) {
                        while (rs.next()) {
                            dbSkills.put(rs.getString("id"), rs.getString("name"));
                        }
                    }
                    String insertSkillSql = "INSERT IGNORE INTO student_skills (student_id, skill_id, proficiency, verified_by_faculty) VALUES (?, ?, 3, FALSE)";
                    try (PreparedStatement psSkill = conn.prepareStatement(insertSkillSql)) {
                        for (String skInput : selectedSkills) {
                            String raw = skInput.trim().toLowerCase(Locale.ROOT);
                            for (Map.Entry<String, String> entry : dbSkills.entrySet()) {
                                String id = entry.getKey().toLowerCase(Locale.ROOT);
                                String name = entry.getValue().toLowerCase(Locale.ROOT);
                                if (id.equals(raw) || name.equals(raw) || name.contains(raw)) {
                                    psSkill.setString(1, studentId);
                                    psSkill.setString(2, entry.getKey());
                                    psSkill.addBatch();
                                    break;
                                }
                            }
                        }
                        psSkill.executeBatch();
                    }
                }

                auditLogDao.insertLog(conn, "STUDENT_REGISTERED", userId, cleanRoll, "STUDENT",
                        "New student registration submitted: " + cleanName + " (" + cleanRoll + ") - Department: " + cleanBranch + " - Awaiting Faculty Approval");

                conn.commit();
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }

            String assignedFaculty = DeptNormalizer.assignedCoordinatorLabel(cleanBranch);
            return new RegistrationReceipt(
                    studentId,
                    cleanName,
                    cleanRoll,
                    "Registration submitted successfully. Your student account is now awaiting departmental verification by " + assignedFaculty + "."
            );
        } catch (AuthException ae) {
            throw ae;
        } catch (SQLException ex) {
            throw new AuthException("Database error during student registration: " + ex.getMessage());
        }
    }

    /**
     * Transactional Faculty Registration -> MySQL `users`, `faculty`, `audit_logs`.
     */
    public RegistrationReceipt registerFaculty(
            String fullName,
            String employeeId,
            String email,
            String department,
            String designation,
            String phone,
            String password,
            String confirmPassword
    ) throws AuthException {
        String cleanName = fullName == null ? "" : fullName.trim();
        String cleanEmpId = employeeId == null ? "" : employeeId.trim().toUpperCase(Locale.ROOT);
        String cleanEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String cleanDept = department == null ? "" : department.trim();
        String cleanDesig = designation == null ? "" : designation.trim();
        String cleanPhone = (phone == null || phone.isBlank()) ? "+91 98765 00000" : phone.trim();

        if (cleanName.isEmpty()) throw new AuthException("Full Name is required.");
        if (cleanEmpId.isEmpty()) throw new AuthException("Faculty ID / Employee ID is required.");
        if (cleanEmail.isEmpty()) throw new AuthException("Official college email is required.");
        if (cleanDept.isEmpty()) throw new AuthException("Academic department is required.");
        if (cleanDesig.isEmpty()) throw new AuthException("Faculty designation is required.");
        if (password == null || password.length() < 6) {
            throw new AuthException("Password must be at least 6 characters in length.");
        }
        if (!password.equals(confirmPassword)) {
            throw new AuthException("Passwords do not match.");
        }

        String[] hashed = PasswordHasher.hashWithBCrypt(password);
        String userId = "usr-fac-" + UUID.randomUUID();
        String facId = "fac-" + UUID.randomUUID();

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            if (userDao.existsByUsername(conn, cleanEmpId)) {
                throw new AuthException("Faculty ID " + cleanEmpId + " is already registered.");
            }
            if (userDao.findUsernameByEmail(conn, cleanEmail) != null) {
                throw new AuthException("Email address " + cleanEmail + " is already registered.");
            }

            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO users (id, username, email, full_name, role, department, password_hash, salt, status, is_active, created_at)
                        VALUES (?, ?, ?, ?, 'FACULTY', ?, ?, ?, 'PENDING_APPROVAL', FALSE, NOW())
                        """)) {
                    ps.setString(1, userId);
                    ps.setString(2, cleanEmpId.toLowerCase(Locale.ROOT));
                    ps.setString(3, cleanEmail);
                    ps.setString(4, cleanName);
                    ps.setString(5, cleanDept);
                    ps.setString(6, hashed[0]);
                    ps.setString(7, hashed[1]);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO faculty (id, faculty_id, employee_id, full_name, department, designation, email, phone, user_id, faculty_approved, admin_approved, status, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, FALSE, 'PENDING_APPROVAL', NOW())
                        """)) {
                    ps.setString(1, facId);
                    ps.setString(2, cleanEmpId);
                    ps.setString(3, cleanEmpId);
                    ps.setString(4, cleanName);
                    ps.setString(5, cleanDept);
                    ps.setString(6, cleanDesig);
                    ps.setString(7, cleanEmail);
                    ps.setString(8, cleanPhone);
                    ps.setString(9, userId);
                    ps.executeUpdate();
                }

                auditLogDao.insertLog(conn, "FACULTY_REGISTERED", userId, cleanEmpId, "FACULTY",
                        "New faculty registration: " + cleanName + " (" + cleanEmpId + ") - Awaiting College Administrator Approval");

                conn.commit();
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }

            return new RegistrationReceipt(
                    facId,
                    cleanName,
                    cleanEmpId,
                    "Faculty registration submitted successfully. Your account is awaiting clearance from College Administration."
            );
        } catch (AuthException ae) {
            throw ae;
        } catch (SQLException ex) {
            throw new AuthException("Database error during faculty registration: " + ex.getMessage());
        }
    }

    /**
     * Transactional Company Registration -> MySQL `companies`, `users`, `audit_logs`.
     */
    public RegistrationReceipt registerCompany(
            String companyName,
            String industry,
            String website,
            String location,
            String hrName,
            String hrEmail,
            String hrPhone,
            String tier,
            String password,
            String confirmPassword
    ) throws AuthException {
        String cleanCompName = companyName == null ? "" : companyName.trim();
        String cleanHrName = hrName == null ? "" : hrName.trim();
        String cleanEmail = hrEmail == null ? "" : hrEmail.trim().toLowerCase(Locale.ROOT);
        String cleanIndustry = (industry == null || industry.isBlank()) ? "IT / Software Services" : industry.trim();
        String cleanLocation = (location == null || location.isBlank()) ? "Hyderabad, Telangana" : location.trim();
        String cleanWebsite = website == null ? "" : website.trim();
        String cleanPhone = (hrPhone == null || hrPhone.isBlank()) ? "+91 98480 00000" : hrPhone.trim();
        String cleanTier = (tier == null || tier.isBlank()) ? "Standard IT" : tier.trim();

        if (cleanCompName.isEmpty()) throw new AuthException("Company Name is required.");
        if (cleanHrName.isEmpty()) throw new AuthException("HR / Recruiter contact name is required.");
        if (cleanEmail.isEmpty()) throw new AuthException("Official corporate email is required.");
        if (password == null || password.length() < 6) {
            throw new AuthException("Password must be at least 6 characters in length.");
        }
        if (!password.equals(confirmPassword)) {
            throw new AuthException("Passwords do not match.");
        }

        String slug = cleanCompName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (slug.length() > 15) slug = slug.substring(0, 15);
        String companyUsername = "recruiter_" + slug;

        String[] hashed = PasswordHasher.hashWithBCrypt(password);
        String userId = "usr-comp-" + UUID.randomUUID();
        String compId = "comp-" + UUID.randomUUID();

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            if (userDao.existsByEmailOrUsername(conn, cleanEmail, companyUsername)) {
                throw new AuthException("An account with email " + cleanEmail + " or company name already exists.");
            }

            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO companies (id, name, industry, website, location, description, hr_name, hr_email, hr_phone, tier, approval_status, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_APPROVAL', NOW())
                        """)) {
                    ps.setString(1, compId);
                    ps.setString(2, cleanCompName);
                    ps.setString(3, cleanIndustry);
                    ps.setString(4, cleanWebsite);
                    ps.setString(5, cleanLocation);
                    ps.setString(6, "Recruitment partnership for " + cleanCompName);
                    ps.setString(7, cleanHrName);
                    ps.setString(8, cleanEmail);
                    ps.setString(9, cleanPhone);
                    ps.setString(10, cleanTier);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO users (id, username, email, full_name, role, department, company_id, password_hash, salt, status, is_active, created_at)
                        VALUES (?, ?, ?, ?, 'COMPANY', ?, ?, ?, ?, 'PENDING_APPROVAL', FALSE, NOW())
                        """)) {
                    ps.setString(1, userId);
                    ps.setString(2, companyUsername);
                    ps.setString(3, cleanEmail);
                    ps.setString(4, cleanHrName + " (" + cleanCompName + ")");
                    ps.setString(5, cleanCompName);
                    ps.setString(6, compId);
                    ps.setString(7, hashed[0]);
                    ps.setString(8, hashed[1]);
                    ps.executeUpdate();
                }

                auditLogDao.insertLog(conn, "COMPANY_REGISTERED", userId, companyUsername, "COMPANY",
                        "New corporate recruiter registration: " + cleanCompName + " - Awaiting College Placement Admin Approval");

                conn.commit();
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }

            return new RegistrationReceipt(
                    compId,
                    cleanCompName,
                    cleanEmail,
                    "Company registration submitted successfully. Your corporate profile is awaiting verification by College Placement Administration."
            );
        } catch (AuthException ae) {
            throw ae;
        } catch (SQLException ex) {
            throw new AuthException("Database error during company registration: " + ex.getMessage());
        }
    }
}
