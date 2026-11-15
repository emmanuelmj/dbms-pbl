package edu.gcet.placementhub.services;

import com.mongodb.MongoException;
import com.mongodb.client.MongoDatabase;
import edu.gcet.placementhub.dao.AuditLogDao;
import edu.gcet.placementhub.dao.CompanyDao;
import edu.gcet.placementhub.dao.FacultyDao;
import edu.gcet.placementhub.dao.StudentDao;
import edu.gcet.placementhub.dao.UserDao;
import edu.gcet.placementhub.database.DatabaseConnection;
import edu.gcet.placementhub.database.DatabaseException;
import edu.gcet.placementhub.models.Company;
import edu.gcet.placementhub.models.Faculty;
import edu.gcet.placementhub.models.PortalCategory;
import edu.gcet.placementhub.models.Student;
import edu.gcet.placementhub.models.StudentSkill;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.models.UserRole;
import edu.gcet.placementhub.utils.DeptNormalizer;
import edu.gcet.placementhub.utils.PasswordHasher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Complete authentication & registration workflows backed by MongoDB Atlas / Community.
 * Strictly authenticates against MongoDB `users` and `students` with BCrypt + legacy hash verification.
 */
public class AuthService {

    private static final String INVALID_CREDENTIALS_MSG = "Incorrect username or password. Please try again.";

    private final UserDao userDao = new UserDao();
    private final StudentDao studentDao = new StudentDao();
    private final FacultyDao facultyDao = new FacultyDao();
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

        public User getUser() {
            return user;
        }

        public Student getStudentRecord() {
            return studentRecord;
        }
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

        public String getPrimaryId() {
            return primaryId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getLoginHint() {
            return loginHint;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Authenticates a user directly against MongoDB `users` (and `students` if applicable).
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

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            User user = userDao.findByLoginIdentifier(db, cleanUsername);
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

            Student studentRecord = studentDao.findByRollNumber(db, cleanUsername);
            if (studentRecord == null && user.getRole() == UserRole.STUDENT) {
                studentRecord = studentDao.findByUserId(db, user.getId());
                if (studentRecord == null && user.getStudentRollNumber() != null) {
                    studentRecord = studentDao.findByRollNumber(db, user.getStudentRollNumber());
                }
                if (studentRecord == null && user.getUsername() != null) {
                    studentRecord = studentDao.findByRollNumber(db, user.getUsername());
                }
                if (studentRecord == null && user.getEmail() != null) {
                    studentRecord = studentDao.findByEmail(db, user.getEmail());
                }
            }

            // Approval & status gates
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
                    userDao.activateApprovedStudentUser(db, user.getId());
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
                    Company linkedCompany = companyDao.findById(db, user.getCompanyId());
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
            userDao.updateLastLogin(db, user.getId());
            auditLogDao.insertLog(db, "USER_LOGIN", user.getId(), user.getUsername(), user.getRole().name(),
                    "Authenticated via Java Swing desktop client (" + (portalRole != null ? portalRole.name() : user.getRole().name()) + ")");

            // Strip sensitive fields before returning
            user.setPasswordHash(null);
            user.setSalt(null);
            return new AuthResult(user, studentRecord);
        } catch (AuthException ae) {
            throw ae;
        } catch (DatabaseException | MongoException ex) {
            throw new AuthException("MongoDB connection failed: " + ex.getMessage()
                    + "\nPlease check your .env configuration (MONGO_URI) or MongoDB Atlas IP whitelist (0.0.0.0/0).");
        }
    }

    /**
     * Explicit, user-initiated password recovery/update for an account in MongoDB.
     */
    public String recoverAndSetPassword(
            String identifier,
            String registeredEmail,
            String dbPasswordUnused,
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

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            User target = userDao.findByLoginIdentifier(db, cleanId);
            if (target == null) {
                throw new AuthException("No user account found in MongoDB matching '" + cleanId + "'.");
            }
            String storedEmail = target.getEmail() == null ? "" : target.getEmail().trim().toLowerCase(Locale.ROOT);
            if (!storedEmail.equals(cleanEmail)) {
                throw new AuthException("Registered email does not match the email stored for account '@" + target.getUsername() + "'.");
            }

            String[] hashed = PasswordHasher.hashWithBCrypt(newPassword);
            userDao.updatePasswordHash(db, target.getId(), hashed[0], hashed[1]);
            auditLogDao.insertLog(db, "PASSWORD_RECOVERY_UPDATED", target.getId(), target.getUsername(),
                    target.getRole().name(), "Password explicitly updated via MongoDB-verified account recovery dialog.");

            String statusNote = "";
            String st = target.getStatus() == null ? "" : target.getStatus().trim().toUpperCase(Locale.ROOT);
            if ("PENDING".equals(st) || "PENDING_APPROVAL".equals(st) || "REJECTED".equals(st) || !target.isActive()) {
                statusNote = "\nNote: Account approval status is currently '" + (target.getStatus() != null ? target.getStatus() : "UNKNOWN")
                        + "' (isActive=" + target.isActive() + "). Approval gates are preserved and still apply.";
            }

            return "Password updated with BCrypt for @" + target.getUsername() + " (" + target.getRole().name() + ")."
                    + statusNote + "\nYou can now sign in manually using your new password.";
        } catch (AuthException ae) {
            throw ae;
        } catch (DatabaseException | MongoException ex) {
            throw new AuthException("MongoDB error during password recovery: " + ex.getMessage());
        }
    }

    /**
     * Transactional Student Registration -> MongoDB `students` (embedded arrays) + `users` + `audit_logs`.
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
        String idSuffix = cleanRoll.length() >= 4 ? cleanRoll.substring(cleanRoll.length() - 4) : cleanRoll;
        String idCard = "GCET-" + graduationYear + "-" + idSuffix;

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            String existingName = studentDao.findNameByRollNumber(db, cleanRoll);
            if (existingName != null) {
                throw new AuthException("Roll number " + cleanRoll + " is already registered to " + existingName + ".");
            }
            String existingUser = userDao.findUsernameByEmail(db, cleanEmail);
            if (existingUser != null) {
                throw new AuthException("Email address " + cleanEmail + " is already registered with account (" + existingUser + ").");
            }

            Student student = new Student();
            student.setRollNumber(cleanRoll);
            student.setStudentIdCard(idCard);
            student.setFullName(cleanName);
            student.setEmail(cleanEmail);
            student.setPhone(cleanPhone);
            student.setBranch(cleanBranch);
            student.setAcademicYear(academicYear);
            student.setYearOfStudy(academicYear);
            student.setCgpa(Math.round(cgpa * 100.0) / 100.0);
            student.setTenthPercentage(85.0);
            student.setInterPercentage(88.0);
            student.setGraduationYear(graduationYear);
            student.setPlacementStatus("UNPLACED");
            student.setApprovalStatus("PENDING_APPROVAL");

            if (selectedSkills != null && !selectedSkills.isEmpty()) {
                List<StudentSkill> skillsList = new ArrayList<>();
                for (String skId : selectedSkills) {
                    StudentSkill ss = new StudentSkill();
                    ss.setSkillId(skId);
                    ss.setProficiency(3);
                    ss.setVerifiedByFaculty(false);
                    skillsList.add(ss);
                }
                student.setSkills(skillsList);
            }

            studentDao.insertStudentWithUser(db, student, passwordHash, salt);
            auditLogDao.insertLog(db, "STUDENT_REGISTERED", student.getId(), cleanRoll, "STUDENT",
                    "New student self-registration: " + cleanName + " (" + cleanRoll + ") - Awaiting Department Faculty Approval");

            return new RegistrationReceipt(
                    student.getId(),
                    cleanName,
                    cleanRoll,
                    "Student registration submitted successfully. Your account is awaiting Department Faculty Coordinator verification."
            );
        } catch (AuthException ae) {
            throw ae;
        } catch (DatabaseException | MongoException ex) {
            throw new AuthException("Database error during student registration: " + ex.getMessage());
        }
    }

    /**
     * Transactional Faculty Registration -> MongoDB `users`, `faculty`, `audit_logs`.
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

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            if (userDao.existsByUsername(db, cleanEmpId)) {
                throw new AuthException("Faculty ID " + cleanEmpId + " is already registered.");
            }
            if (userDao.findUsernameByEmail(db, cleanEmail) != null) {
                throw new AuthException("Email address " + cleanEmail + " is already registered.");
            }

            User u = new User();
            u.setUsername(cleanEmpId.toLowerCase(Locale.ROOT));
            u.setEmail(cleanEmail);
            u.setFullName(cleanName);
            u.setRole(UserRole.FACULTY);
            u.setDepartment(cleanDept);
            u.setDesignation(cleanDesig);
            u.setEmployeeId(cleanEmpId);
            u.setPhone(cleanPhone);
            u.setStatus("PENDING_APPROVAL");
            u.setActive(false);

            String userId = userDao.insertUser(db, u, hashed[0], hashed[1]);

            Faculty f = new Faculty();
            f.setFacultyId(cleanEmpId);
            f.setEmployeeId(cleanEmpId);
            f.setFullName(cleanName);
            f.setDepartment(cleanDept);
            f.setDesignation(cleanDesig);
            f.setEmail(cleanEmail);
            f.setPhone(cleanPhone);
            f.setUserId(userId);
            f.setFacultyApproved(true);
            f.setAdminApproved(false);
            f.setStatus("PENDING_APPROVAL");

            String facId = facultyDao.insertFaculty(db, f);

            auditLogDao.insertLog(db, "FACULTY_REGISTERED", userId, cleanEmpId, "FACULTY",
                    "New faculty registration: " + cleanName + " (" + cleanEmpId + ") - Awaiting College Administrator Approval");

            return new RegistrationReceipt(
                    facId,
                    cleanName,
                    cleanEmpId,
                    "Faculty registration submitted successfully. Your account is awaiting clearance from College Administration."
            );
        } catch (AuthException ae) {
            throw ae;
        } catch (DatabaseException | MongoException ex) {
            throw new AuthException("Database error during faculty registration: " + ex.getMessage());
        }
    }

    /**
     * Transactional Company Registration -> MongoDB `companies`, `users`, `audit_logs`.
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

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            if (userDao.existsByEmailOrUsername(db, cleanEmail, companyUsername)) {
                throw new AuthException("An account with email " + cleanEmail + " or company name already exists.");
            }

            Company c = new Company();
            c.setName(cleanCompName);
            c.setIndustry(cleanIndustry);
            c.setWebsite(cleanWebsite);
            c.setLocation(cleanLocation);
            c.setDescription("Recruitment partnership for " + cleanCompName);
            c.setHrName(cleanHrName);
            c.setHrEmail(cleanEmail);
            c.setHrPhone(cleanPhone);
            c.setTier(cleanTier);
            c.setApprovalStatus("PENDING_APPROVAL");

            String compId = companyDao.insertCompany(db, c, null, null);

            User u = new User();
            u.setUsername(companyUsername);
            u.setEmail(cleanEmail);
            u.setFullName(cleanHrName + " (" + cleanCompName + ")");
            u.setRole(UserRole.COMPANY);
            u.setDepartment(cleanCompName);
            u.setCompanyId(compId);
            u.setCompanyName(cleanCompName);
            u.setStatus("PENDING_APPROVAL");
            u.setActive(false);

            String userId = userDao.insertUser(db, u, hashed[0], hashed[1]);

            auditLogDao.insertLog(db, "COMPANY_REGISTERED", userId, companyUsername, "COMPANY",
                    "New corporate recruiter registration: " + cleanCompName + " - Awaiting College Placement Admin Approval");

            return new RegistrationReceipt(
                    compId,
                    cleanCompName,
                    cleanEmail,
                    "Company registration submitted successfully. Your corporate profile is awaiting verification by College Placement Administration."
            );
        } catch (AuthException ae) {
            throw ae;
        } catch (DatabaseException | MongoException ex) {
            throw new AuthException("Database error during company registration: " + ex.getMessage());
        }
    }
}
