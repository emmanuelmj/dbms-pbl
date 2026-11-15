package edu.gcet.placementhub.dao;

import edu.gcet.placementhub.db.DatabaseConnection;
import edu.gcet.placementhub.model.*;

import java.net.URI;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * GCET PLACEMENT HUB - Unified Profile Management DAO
 * Provides transactional MySQL 8 persistence across all institutional roles:
 * STUDENT, FACULTY, COMPANY, PRINCIPAL, CHAIRMAN, VICE_CHAIRMAN, ADMIN, PLACEMENT_OFFICER.
 */
public class ProfileDao {
    private static final Logger LOGGER = Logger.getLogger(ProfileDao.class.getName());
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+\\d\\s\\-().]{7,20}$");

    public UserProfileData loadFullProfile(String userId) throws SQLException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required.");
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            User user = loadSanitizedUser(conn, userId.trim());
            if (user == null) {
                return null;
            }

            UserProfileData data = new UserProfileData();
            data.setUser(user);

            loadUserProfileMetadata(conn, user.getId(), data);
            data.setCustomLinks(loadCustomLinks(conn, user.getId()));

            User.Role role = user.getRole();
            if (role == User.Role.STUDENT) {
                Student student = loadStudentForUser(conn, user);
                data.setStudent(student);
                if (student != null) {
                    if (isBlank(data.getPhone())) data.setPhone(student.getPhone());
                    if (isBlank(data.getLocation())) data.setLocation(student.getLocation());
                    if (isBlank(data.getAddress())) data.setAddress(student.getAddress());
                    if (isBlank(data.getAboutMe())) data.setAboutMe(student.getAboutMe());
                    if (isBlank(data.getDateOfBirth())) data.setDateOfBirth(student.getDob());
                    if (isBlank(data.getGender())) data.setGender(student.getGender());

                    StudentDao studentDao = new StudentDao();
                    student.setSkills(studentDao.loadStudentSkills(conn, student.getId()));
                    data.setProjects(loadProjects(conn, student.getId()));
                    data.setInternships(loadInternships(conn, student.getId()));
                    data.setCertifications(loadCertifications(conn, user.getId(), student.getId()));
                    data.setAchievements(loadAchievements(conn, student.getId()));
                }
            } else if (role == User.Role.FACULTY) {
                Faculty faculty = loadFacultyForUser(conn, user);
                data.setFaculty(faculty);
                if (faculty != null) {
                    if (isBlank(data.getPhone())) data.setPhone(faculty.getPhone());
                    if (isBlank(data.getDesignation())) data.setDesignation(faculty.getDesignation());
                    if (isBlank(data.getLocation())) data.setLocation(faculty.getLocation());
                    if (isBlank(data.getAboutMe())) data.setAboutMe(faculty.getAboutMe());
                    data.setPublications(loadPublications(conn, user.getId(), faculty.getId()));
                } else {
                    data.setPublications(loadPublications(conn, user.getId(), null));
                }
                data.setCertifications(loadCertifications(conn, user.getId(), null));
            } else if (role == User.Role.COMPANY) {
                Company company = loadCompanyForUser(conn, user);
                data.setCompany(company);
                if (company != null) {
                    if (isBlank(data.getPhone())) data.setPhone(company.getHrPhone());
                    if (isBlank(data.getDesignation())) data.setDesignation(company.getRecruiterDesignation());
                    if (isBlank(data.getLocation())) data.setLocation(company.getLocation());
                    if (isBlank(data.getLinkedinUrl())) data.setLinkedinUrl(company.getLinkedinUrl());
                }
            }

            computeCompletion(data);
            return data;
        }
    }

    public UserProfileData loadStudentProfileByStudentId(String studentIdOrRoll) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT * FROM students WHERE id = ? OR LOWER(roll_number) = LOWER(?) LIMIT 1";
            Student student = null;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, studentIdOrRoll);
                ps.setString(2, studentIdOrRoll);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        student = mapStudentRow(rs);
                    }
                }
            }
            if (student == null) return null;

            if (student.getUserId() != null && !student.getUserId().trim().isEmpty()) {
                UserProfileData full = loadFullProfile(student.getUserId());
                if (full != null) {
                    if (full.getStudent() == null) full.setStudent(student);
                    return full;
                }
            }

            UserProfileData data = new UserProfileData();
            data.setStudent(student);
            data.setPhone(student.getPhone());
            data.setLocation(student.getLocation());
            data.setAddress(student.getAddress());
            data.setAboutMe(student.getAboutMe());
            data.setDateOfBirth(student.getDob());
            data.setGender(student.getGender());

            StudentDao sDao = new StudentDao();
            student.setSkills(sDao.loadStudentSkills(conn, student.getId()));
            data.setProjects(loadProjects(conn, student.getId()));
            data.setInternships(loadInternships(conn, student.getId()));
            data.setCertifications(loadCertifications(conn, null, student.getId()));
            data.setAchievements(loadAchievements(conn, student.getId()));
            computeCompletion(data);
            return data;
        }
    }

    public UserProfileData saveFullProfile(User actor, UserProfileData data) throws Exception {
        if (actor == null || data == null || data.getUser() == null) {
            throw new SecurityException("Authentication is required to update a profile.");
        }
        if (!actor.getId().equals(data.getUser().getId())) {
            throw new SecurityException("Security violation: You are only authorized to edit your own profile.");
        }

        validateProfileData(actor, data);

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Update permitted fields on users (never touch username, role, status, is_active, password_hash, salt)
                String cleanFullName = data.getUser().getFullName().trim();
                String cleanEmail = data.getUser().getEmail().trim().toLowerCase();

                String dupSql = "SELECT id FROM users WHERE LOWER(email) = LOWER(?) AND id != ? LIMIT 1";
                try (PreparedStatement psDup = conn.prepareStatement(dupSql)) {
                    psDup.setString(1, cleanEmail);
                    psDup.setString(2, actor.getId());
                    try (ResultSet rs = psDup.executeQuery()) {
                        if (rs.next()) {
                            throw new IllegalArgumentException("This email address is already linked to another user account.");
                        }
                    }
                }

                try (PreparedStatement psUser = conn.prepareStatement("UPDATE users SET full_name = ?, email = ? WHERE id = ?")) {
                    psUser.setString(1, cleanFullName);
                    psUser.setString(2, cleanEmail);
                    psUser.setString(3, actor.getId());
                    psUser.executeUpdate();
                }

                // 2. Upsert user_profiles
                upsertUserProfileMetadata(conn, actor.getId(), data);

                // 3. Replace custom links
                replaceCustomLinks(conn, actor.getId(), data.getCustomLinks());

                // 4. Role-specific transactional updates with protected institutional fields
                User.Role role = actor.getRole();
                if (role == User.Role.STUDENT) {
                    saveStudentProfilePart(conn, actor, data, cleanFullName, cleanEmail);
                } else if (role == User.Role.FACULTY) {
                    saveFacultyProfilePart(conn, actor, data, cleanFullName, cleanEmail);
                } else if (role == User.Role.COMPANY) {
                    saveCompanyProfilePart(conn, actor, data, cleanFullName, cleanEmail);
                }

                // 5. Audit log
                insertAuditLog(conn, actor, "PROFILE_UPDATED", "Updated personal/professional profile via Java Swing Desktop App");

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        return loadFullProfile(actor.getId());
    }

    public void updateProfilePhoto(User actor, String target, String dataUrl) throws Exception {
        if (actor == null) throw new SecurityException("Authentication required.");
        if (dataUrl != null && !dataUrl.trim().isEmpty()) {
            String trimmed = dataUrl.trim();
            if (!trimmed.startsWith("data:image/png;base64,") &&
                !trimmed.startsWith("data:image/jpeg;base64,") &&
                !trimmed.startsWith("data:image/jpg;base64,") &&
                !trimmed.startsWith("data:image/webp;base64,")) {
                throw new IllegalArgumentException("Only PNG, JPEG, or WEBP image data is permitted.");
            }
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if ("company_logo".equalsIgnoreCase(target)) {
                    if (actor.getRole() != User.Role.COMPANY) {
                        throw new SecurityException("Only corporate recruiter accounts may update company logos.");
                    }
                    String upd = "UPDATE companies SET company_logo = ? WHERE id = ? OR user_id = ? OR LOWER(hr_email) = LOWER(?)";
                    try (PreparedStatement ps = conn.prepareStatement(upd)) {
                        ps.setString(1, isBlank(dataUrl) ? null : dataUrl.trim());
                        ps.setString(2, actor.getCompanyId() != null ? actor.getCompanyId() : "");
                        ps.setString(3, actor.getId());
                        ps.setString(4, actor.getEmail() != null ? actor.getEmail() : "");
                        ps.executeUpdate();
                    }
                } else {
                    String upsert = "INSERT INTO user_profiles (user_id, profile_photo) VALUES (?, ?) " +
                                    "ON DUPLICATE KEY UPDATE profile_photo = VALUES(profile_photo)";
                    try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                        ps.setString(1, actor.getId());
                        ps.setString(2, isBlank(dataUrl) ? null : dataUrl.trim());
                        ps.executeUpdate();
                    }
                }
                insertAuditLog(conn, actor, isBlank(dataUrl) ? "PROFILE_PHOTO_REMOVED" : "PROFILE_PHOTO_UPDATED", "Target: " + target);
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // =========================================================================
    // VALIDATION & COMPLETION ENGINE
    // =========================================================================

    public static void validateProfileData(User actor, UserProfileData data) {
        if (isBlank(data.getUser().getFullName())) {
            throw new IllegalArgumentException("Full Name is required.");
        }
        String email = safeTrim(data.getUser().getEmail()).toLowerCase();
        if (email.isEmpty() || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }
        String phone = safeTrim(data.getPhone());
        if (!phone.isEmpty() && !PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("Phone number must be 7-20 characters and contain only digits, spaces, +, -, or parentheses.");
        }

        validateOptionalUrl(data.getLinkedinUrl(), "LinkedIn URL");
        validateOptionalUrl(data.getGithubUrl(), "GitHub URL");
        validateOptionalUrl(data.getPortfolioUrl(), "Personal Portfolio URL");
        validateOptionalUrl(data.getLeetcodeUrl(), "LeetCode URL");
        validateOptionalUrl(data.getHackerrankUrl(), "HackerRank URL");
        validateOptionalUrl(data.getCodechefUrl(), "CodeChef URL");
        validateOptionalUrl(data.getGfgUrl(), "GeeksforGeeks URL");
        validateOptionalUrl(data.getKaggleUrl(), "Kaggle URL");
        validateOptionalUrl(data.getGoogleDevUrl(), "Google Developer URL");

        if (data.getCustomLinks() != null) {
            for (CustomLink link : data.getCustomLinks()) {
                if (isBlank(link.getTitle()) && isBlank(link.getUrl())) continue;
                if (isBlank(link.getTitle())) {
                    throw new IllegalArgumentException("Each custom link requires a title.");
                }
                validateRequiredUrl(link.getUrl(), "Custom link \"" + link.getTitle() + "\"");
            }
        }

        if (actor.getRole() == User.Role.STUDENT && data.getStudent() != null) {
            Student st = data.getStudent();
            if (st.getCgpa() < 0.0 || st.getCgpa() > 10.0) {
                throw new IllegalArgumentException("CGPA must be between 0.00 and 10.00.");
            }
            if (st.getBacklogs() < 0) {
                throw new IllegalArgumentException("Active backlogs cannot be negative.");
            }
            if (st.getCurrentSemester() < 1 || st.getCurrentSemester() > 8) {
                throw new IllegalArgumentException("Current semester must be between 1 and 8.");
            }
            if (st.getAcademicYear() < 1 || st.getAcademicYear() > 4) {
                throw new IllegalArgumentException("Academic year must be between 1 and 4.");
            }
            validateOptionalUrl(st.getResumeUrl(), "Resume URL");

            if (data.getProjects() != null) {
                for (StudentProject p : data.getProjects()) {
                    if (isBlank(p.getTitle())) {
                        throw new IllegalArgumentException("Every project requires a title.");
                    }
                    validateOptionalUrl(p.getProjectUrl(), "Project \"" + p.getTitle() + "\" URL");
                }
            }
            if (data.getInternships() != null) {
                for (StudentInternship intern : data.getInternships()) {
                    if (isBlank(intern.getCompanyName()) || isBlank(intern.getRole())) {
                        throw new IllegalArgumentException("Every internship entry requires company name and role.");
                    }
                }
            }
            if (data.getCertifications() != null) {
                for (UserCertification cert : data.getCertifications()) {
                    if (isBlank(cert.getName()) || isBlank(cert.getIssuingOrg())) {
                        throw new IllegalArgumentException("Every certification requires certificate name and issuing organization.");
                    }
                    validateOptionalUrl(cert.getCredentialUrl(), "Certification \"" + cert.getName() + "\" URL");
                }
            }
            if (data.getAchievements() != null) {
                for (StudentAchievement ach : data.getAchievements()) {
                    if (isBlank(ach.getTitle())) {
                        throw new IllegalArgumentException("Every achievement/workshop requires a title.");
                    }
                    validateOptionalUrl(ach.getCredentialUrl(), "Achievement \"" + ach.getTitle() + "\" URL");
                }
            }
        } else if (actor.getRole() == User.Role.FACULTY) {
            if (data.getPublications() != null) {
                for (FacultyPublication pub : data.getPublications()) {
                    if (isBlank(pub.getTitle())) {
                        throw new IllegalArgumentException("Every publication requires a title.");
                    }
                    validateOptionalUrl(pub.getDoiOrUrl(), "Publication \"" + pub.getTitle() + "\" DOI/URL");
                }
            }
            if (data.getCertifications() != null) {
                for (UserCertification cert : data.getCertifications()) {
                    if (isBlank(cert.getName()) || isBlank(cert.getIssuingOrg())) {
                        throw new IllegalArgumentException("Every certification requires certificate name and issuing organization.");
                    }
                    validateOptionalUrl(cert.getCredentialUrl(), "Certification \"" + cert.getName() + "\" URL");
                }
            }
        } else if (actor.getRole() == User.Role.COMPANY && data.getCompany() != null) {
            Company c = data.getCompany();
            if (isBlank(c.getName())) {
                throw new IllegalArgumentException("Company name is required.");
            }
            validateOptionalUrl(c.getWebsite(), "Company Website");
            validateOptionalUrl(c.getLinkedinUrl(), "Company LinkedIn URL");
        }
    }

    public static void validateOptionalUrl(String url, String fieldName) {
        if (isBlank(url)) return;
        validateRequiredUrl(url, fieldName);
    }

    public static void validateRequiredUrl(String url, String fieldName) {
        String trimmed = safeTrim(url);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
            throw new IllegalArgumentException(fieldName + " must start with http:// or https://.");
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException(fieldName + " must start with http:// or https://.");
            }
            if (host == null || (!host.contains(".") && !host.equalsIgnoreCase("localhost"))) {
                throw new IllegalArgumentException(fieldName + " must contain a valid domain name (e.g. https://github.com/username).");
            }
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith(fieldName)) throw e;
            throw new IllegalArgumentException(fieldName + " is not a valid URL. Include https:// or http://.");
        }
    }

    public static void computeCompletion(UserProfileData data) {
        if (data == null) return;
        List<String> missing = new ArrayList<>();
        int total = 0;
        int done = 0;

        boolean hasPhoto = !isBlank(data.getProfilePhoto()) || (data.getCompany() != null && !isBlank(data.getCompany().getCompanyLogo()));
        total++; if (hasPhoto) done++; else missing.add("Profile Photo");

        boolean hasName = data.getUser() != null && !isBlank(data.getUser().getFullName());
        total++; if (hasName) done++; else missing.add("Full Name");

        boolean hasEmail = data.getUser() != null && !isBlank(data.getUser().getEmail());
        total++; if (hasEmail) done++; else missing.add("Email Address");

        total++; if (!isBlank(data.getPhone())) done++; else missing.add("Phone Number");
        total++; if (!isBlank(data.getLocation())) done++; else missing.add("Location");
        total++; if (!isBlank(data.getAboutMe())) done++; else missing.add("About Me / Bio");

        boolean hasSocial = !isBlank(data.getLinkedinUrl()) || !isBlank(data.getGithubUrl()) || !isBlank(data.getPortfolioUrl())
                || !isBlank(data.getLeetcodeUrl()) || !isBlank(data.getHackerrankUrl()) || !isBlank(data.getCodechefUrl())
                || !isBlank(data.getGfgUrl()) || !isBlank(data.getKaggleUrl()) || !isBlank(data.getGoogleDevUrl())
                || (data.getCustomLinks() != null && !data.getCustomLinks().isEmpty());
        total++; if (hasSocial) done++; else missing.add("Social / Professional Link");

        User.Role role = data.getUser() != null ? data.getUser().getRole() : User.Role.STUDENT;
        if (role == User.Role.STUDENT && data.getStudent() != null) {
            Student st = data.getStudent();
            total++; if (!isBlank(data.getDateOfBirth()) || !isBlank(data.getAddress())) done++; else missing.add("DOB / Address");
            total++; if (st.getCgpa() > 0 && st.getCurrentSemester() > 0) done++; else missing.add("Academic Details");
            boolean hasSkills = (st.getSkills() != null && !st.getSkills().isEmpty())
                    || !isBlank(st.getTechnicalSkills()) || !isBlank(st.getProgrammingLanguages())
                    || !isBlank(st.getFrameworksTechnologies());
            total++; if (hasSkills) done++; else missing.add("Technical Skills");
            total++; if (data.getProjects() != null && !data.getProjects().isEmpty()) done++; else missing.add("Projects");
            total++; if (data.getCertifications() != null && !data.getCertifications().isEmpty()) done++; else missing.add("Certifications");
            total++; if (!isBlank(st.getCareerInterests()) || !isBlank(st.getPreferredJobRoles())) done++; else missing.add("Career Interests & Roles");
        } else if (role == User.Role.FACULTY && data.getFaculty() != null) {
            Faculty f = data.getFaculty();
            total++; if (!isBlank(f.getQualifications())) done++; else missing.add("Qualifications");
            total++; if (!isBlank(f.getAreasOfExpertise())) done++; else missing.add("Areas of Expertise");
            total++; if (!isBlank(f.getSubjectsHandled())) done++; else missing.add("Subjects Handled");
            total++; if (!isBlank(f.getResearchInterests())) done++; else missing.add("Research Interests");
            boolean hasPubOrCert = (data.getPublications() != null && !data.getPublications().isEmpty())
                    || (data.getCertifications() != null && !data.getCertifications().isEmpty());
            total++; if (hasPubOrCert) done++; else missing.add("Publications / Certifications");
        } else if (role == User.Role.COMPANY && data.getCompany() != null) {
            Company c = data.getCompany();
            total++; if (!isBlank(c.getName()) && !isBlank(c.getIndustry())) done++; else missing.add("Company Name & Industry");
            total++; if (!isBlank(c.getWebsite())) done++; else missing.add("Company Website");
            total++; if (!isBlank(c.getDescription())) done++; else missing.add("Company Description");
            total++; if (!isBlank(c.getCompanyLogo())) done++; else missing.add("Company Logo");
        }

        int pct = total > 0 ? (int) Math.round((done * 100.0) / total) : 0;
        data.setCompletionPercentage(Math.min(100, Math.max(0, pct)));
        data.setMissingSections(missing);
    }

    // =========================================================================
    // INTERNAL LOADERS
    // =========================================================================

    private User loadSanitizedUser(Connection conn, String userId) throws SQLException {
        String sql = "SELECT id, username, email, full_name, role, department, student_roll_number, company_id, status, is_active, last_login, created_at " +
                     "FROM users WHERE id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User u = new User();
                    u.setId(rs.getString("id"));
                    u.setUsername(rs.getString("username"));
                    u.setEmail(rs.getString("email"));
                    u.setFullName(rs.getString("full_name"));
                    try {
                        u.setRole(User.Role.valueOf(rs.getString("role").toUpperCase()));
                    } catch (Exception ex) {
                        u.setRole(User.Role.STUDENT);
                    }
                    u.setDepartment(rs.getString("department"));
                    u.setStudentRollNumber(rs.getString("student_roll_number"));
                    u.setCompanyId(rs.getString("company_id"));
                    u.setStatus(rs.getString("status"));
                    u.setActive(rs.getBoolean("is_active"));
                    u.setLastLogin(rs.getTimestamp("last_login"));
                    u.setCreatedAt(rs.getTimestamp("created_at"));
                    return u;
                }
            }
        }
        return null;
    }

    private void loadUserProfileMetadata(Connection conn, String userId, UserProfileData data) {
        String sql = "SELECT * FROM user_profiles WHERE user_id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    data.setProfilePhoto(rs.getString("profile_photo"));
                    data.setPhone(rs.getString("phone"));
                    data.setDesignation(rs.getString("designation"));
                    data.setLocation(rs.getString("location"));
                    data.setAddress(rs.getString("address"));
                    data.setAboutMe(rs.getString("about_me"));
                    data.setDateOfBirth(rs.getString("date_of_birth"));
                    data.setGender(rs.getString("gender"));
                    data.setLinkedinUrl(rs.getString("linkedin_url"));
                    data.setGithubUrl(rs.getString("github_url"));
                    data.setPortfolioUrl(rs.getString("portfolio_url"));
                    data.setLeetcodeUrl(rs.getString("leetcode_url"));
                    data.setHackerrankUrl(rs.getString("hackerrank_url"));
                    data.setCodechefUrl(rs.getString("codechef_url"));
                    data.setGfgUrl(rs.getString("gfg_url"));
                    data.setKaggleUrl(rs.getString("kaggle_url"));
                    data.setGoogleDevUrl(rs.getString("google_dev_url"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "user_profiles read fallback: " + e.getMessage());
        }
    }

    private List<CustomLink> loadCustomLinks(Connection conn, String userId) {
        List<CustomLink> list = new ArrayList<>();
        String sql = "SELECT id, user_id, title, url, display_order FROM user_custom_links WHERE user_id = ? ORDER BY display_order ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CustomLink link = new CustomLink(rs.getString("id"), rs.getString("title"), rs.getString("url"));
                    link.setUserId(rs.getString("user_id"));
                    link.setDisplayOrder(rs.getInt("display_order"));
                    list.add(link);
                }
            }
        } catch (SQLException ignored) {}
        return list;
    }

    private Student loadStudentForUser(Connection conn, User user) throws SQLException {
        String sql = "SELECT * FROM students WHERE user_id = ? OR LOWER(roll_number) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getId());
            ps.setString(2, user.getStudentRollNumber() != null ? user.getStudentRollNumber() : user.getUsername());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapStudentRow(rs);
                }
            }
        }
        return null;
    }

    private Student mapStudentRow(ResultSet rs) throws SQLException {
        Student s = new Student();
        s.setId(rs.getString("id"));
        s.setRollNumber(rs.getString("roll_number"));
        s.setFullName(rs.getString("full_name"));
        s.setEmail(rs.getString("email"));
        s.setPhone(rs.getString("phone"));
        s.setBranch(rs.getString("branch"));
        s.setCgpa(rs.getDouble("cgpa"));
        s.setTenthPercentage(rs.getDouble("tenth_percentage"));
        s.setInterPercentage(rs.getDouble("inter_percentage"));
        s.setGraduationYear(rs.getInt("graduation_year"));
        s.setBacklogs(rs.getInt("backlogs"));
        s.setPlacementStatus(rs.getString("placement_status"));
        s.setResumeUrl(rs.getString("resume_url"));
        try {
            s.setSection(rs.getString("section"));
            s.setApprovalStatus(rs.getString("approval_status"));
            s.setUserId(rs.getString("user_id"));
            int ay = rs.getInt("academic_year");
            if (!rs.wasNull() && ay > 0) s.setAcademicYear(ay);
            int sem = rs.getInt("current_semester");
            if (!rs.wasNull() && sem > 0) s.setCurrentSemester(sem);
            s.setDob(rs.getString("dob"));
            s.setGender(rs.getString("gender"));
            s.setAddress(rs.getString("address"));
            s.setLocation(rs.getString("location"));
            s.setAboutMe(rs.getString("about_me"));
            s.setTechnicalSkills(parseListToCsv(rs.getString("technical_skills")));
            s.setProgrammingLanguages(parseListToCsv(rs.getString("programming_languages")));
            s.setFrameworksTechnologies(parseListToCsv(rs.getString("frameworks_technologies")));
            s.setSoftSkills(parseListToCsv(rs.getString("soft_skills")));
            s.setCareerInterests(parseListToCsv(rs.getString("career_interests")));
            s.setPreferredJobRoles(parseListToCsv(rs.getString("preferred_job_roles")));
        } catch (SQLException ignored) {}
        return s;
    }

    private Faculty loadFacultyForUser(Connection conn, User user) {
        String sql = "SELECT * FROM faculty WHERE user_id = ? OR LOWER(email) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getId());
            ps.setString(2, user.getEmail() != null ? user.getEmail() : "");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Faculty f = new Faculty();
                    f.setId(rs.getString("id"));
                    f.setFacultyId(rs.getString("faculty_id"));
                    try { f.setEmployeeId(rs.getString("employee_id")); } catch (SQLException ignored) {}
                    f.setFullName(rs.getString("full_name"));
                    f.setDepartment(rs.getString("department"));
                    f.setDesignation(rs.getString("designation"));
                    f.setEmail(rs.getString("email"));
                    f.setPhone(rs.getString("phone"));
                    f.setUserId(rs.getString("user_id"));
                    f.setStatus(rs.getString("status"));
                    try {
                        f.setFacultyApproved(rs.getBoolean("faculty_approved"));
                        f.setAdminApproved(rs.getBoolean("admin_approved"));
                        f.setAboutMe(rs.getString("about_me"));
                        f.setQualifications(rs.getString("qualifications"));
                        f.setAreasOfExpertise(parseListToCsv(rs.getString("areas_of_expertise")));
                        f.setSubjectsHandled(parseListToCsv(rs.getString("subjects_handled")));
                        f.setResearchInterests(rs.getString("research_interests"));
                        f.setLocation(rs.getString("location"));
                    } catch (SQLException ignored) {}
                    return f;
                }
            }
        } catch (SQLException ignored) {}
        return null;
    }

    private Company loadCompanyForUser(Connection conn, User user) {
        String sql = "SELECT * FROM companies WHERE id = ? OR user_id = ? OR LOWER(hr_email) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getCompanyId() != null ? user.getCompanyId() : "");
            ps.setString(2, user.getId());
            ps.setString(3, user.getEmail() != null ? user.getEmail() : "");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Company c = new Company();
                    c.setId(rs.getString("id"));
                    c.setName(rs.getString("name"));
                    c.setIndustry(rs.getString("industry"));
                    c.setWebsite(rs.getString("website"));
                    c.setLocation(rs.getString("location"));
                    c.setDescription(rs.getString("description"));
                    c.setHrName(rs.getString("hr_name"));
                    c.setHrEmail(rs.getString("hr_email"));
                    c.setHrPhone(rs.getString("hr_phone"));
                    c.setTier(rs.getString("tier"));
                    try {
                        c.setUserId(rs.getString("user_id"));
                        c.setApprovalStatus(rs.getString("approval_status"));
                        c.setRecruiterName(rs.getString("recruiter_name"));
                        c.setRecruiterDesignation(rs.getString("recruiter_designation"));
                        c.setCompanyLogo(rs.getString("company_logo"));
                        c.setLinkedinUrl(rs.getString("linkedin_url"));
                    } catch (SQLException ignored) {}
                    return c;
                }
            }
        } catch (SQLException ignored) {}
        return null;
    }

    private List<StudentProject> loadProjects(Connection conn, String studentId) {
        List<StudentProject> list = new ArrayList<>();
        String sql = "SELECT * FROM student_projects WHERE student_id = ? ORDER BY created_at ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StudentProject p = new StudentProject();
                    p.setId(rs.getString("id"));
                    p.setStudentId(rs.getString("student_id"));
                    p.setUserId(rs.getString("user_id"));
                    p.setTitle(rs.getString("title"));
                    p.setDescription(rs.getString("description"));
                    p.setTechnologies(parseListToCsv(rs.getString("technologies")));
                    p.setProjectUrl(rs.getString("project_url"));
                    p.setDuration(rs.getString("duration"));
                    list.add(p);
                }
            }
        } catch (SQLException ignored) {}
        return list;
    }

    private List<StudentInternship> loadInternships(Connection conn, String studentId) {
        List<StudentInternship> list = new ArrayList<>();
        String sql = "SELECT * FROM student_internships WHERE student_id = ? ORDER BY created_at ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StudentInternship i = new StudentInternship();
                    i.setId(rs.getString("id"));
                    i.setStudentId(rs.getString("student_id"));
                    i.setUserId(rs.getString("user_id"));
                    i.setCompanyName(rs.getString("company_name"));
                    i.setRole(rs.getString("role"));
                    i.setDuration(rs.getString("duration"));
                    i.setLocation(rs.getString("location"));
                    i.setDescription(rs.getString("description"));
                    list.add(i);
                }
            }
        } catch (SQLException ignored) {}
        return list;
    }

    private List<UserCertification> loadCertifications(Connection conn, String userId, String studentId) {
        List<UserCertification> list = new ArrayList<>();
        String sql;
        if (userId != null && studentId != null) {
            sql = "SELECT * FROM user_certifications WHERE user_id = ? OR student_id = ? ORDER BY created_at ASC";
        } else if (userId != null) {
            sql = "SELECT * FROM user_certifications WHERE user_id = ? ORDER BY created_at ASC";
        } else {
            sql = "SELECT * FROM user_certifications WHERE student_id = ? ORDER BY created_at ASC";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (userId != null && studentId != null) {
                ps.setString(1, userId);
                ps.setString(2, studentId);
            } else {
                ps.setString(1, userId != null ? userId : studentId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UserCertification c = new UserCertification();
                    c.setId(rs.getString("id"));
                    c.setUserId(rs.getString("user_id"));
                    c.setStudentId(rs.getString("student_id"));
                    c.setName(rs.getString("name"));
                    c.setIssuingOrg(rs.getString("issuing_org"));
                    c.setIssueDate(rs.getString("issue_date"));
                    c.setCredentialId(rs.getString("credential_id"));
                    c.setCredentialUrl(rs.getString("credential_url"));
                    list.add(c);
                }
            }
        } catch (SQLException ignored) {}
        return list;
    }

    private List<StudentAchievement> loadAchievements(Connection conn, String studentId) {
        List<StudentAchievement> list = new ArrayList<>();
        String sql = "SELECT * FROM student_achievements WHERE student_id = ? ORDER BY created_at ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StudentAchievement a = new StudentAchievement();
                    a.setId(rs.getString("id"));
                    a.setStudentId(rs.getString("student_id"));
                    a.setUserId(rs.getString("user_id"));
                    a.setTitle(rs.getString("title"));
                    a.setCategory(rs.getString("category"));
                    a.setOrganizer(rs.getString("organizer"));
                    a.setEventDate(rs.getString("event_date"));
                    a.setDescription(rs.getString("description"));
                    a.setCredentialUrl(rs.getString("credential_url"));
                    list.add(a);
                }
            }
        } catch (SQLException ignored) {}
        return list;
    }

    private List<FacultyPublication> loadPublications(Connection conn, String userId, String facultyId) {
        List<FacultyPublication> list = new ArrayList<>();
        String sql = "SELECT * FROM faculty_publications WHERE user_id = ? OR (? IS NOT NULL AND faculty_id = ?) ORDER BY created_at ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, facultyId);
            ps.setString(3, facultyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FacultyPublication p = new FacultyPublication();
                    p.setId(rs.getString("id"));
                    p.setFacultyId(rs.getString("faculty_id"));
                    p.setUserId(rs.getString("user_id"));
                    p.setTitle(rs.getString("title"));
                    p.setJournalOrConference(rs.getString("journal_or_conference"));
                    p.setPublicationYear(rs.getString("publication_year"));
                    p.setAuthors(rs.getString("authors"));
                    p.setDoiOrUrl(rs.getString("doi_or_url"));
                    list.add(p);
                }
            }
        } catch (SQLException ignored) {}
        return list;
    }

    // =========================================================================
    // INTERNAL TRANSACTIONAL WRITERS
    // =========================================================================

    private void upsertUserProfileMetadata(Connection conn, String userId, UserProfileData data) throws SQLException {
        String sql = "INSERT INTO user_profiles (" +
                "user_id, profile_photo, phone, designation, location, address, about_me, date_of_birth, gender, " +
                "linkedin_url, github_url, portfolio_url, leetcode_url, hackerrank_url, codechef_url, gfg_url, kaggle_url, google_dev_url" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "profile_photo = VALUES(profile_photo), phone = VALUES(phone), designation = VALUES(designation), " +
                "location = VALUES(location), address = VALUES(address), about_me = VALUES(about_me), " +
                "date_of_birth = VALUES(date_of_birth), gender = VALUES(gender), " +
                "linkedin_url = VALUES(linkedin_url), github_url = VALUES(github_url), portfolio_url = VALUES(portfolio_url), " +
                "leetcode_url = VALUES(leetcode_url), hackerrank_url = VALUES(hackerrank_url), codechef_url = VALUES(codechef_url), " +
                "gfg_url = VALUES(gfg_url), kaggle_url = VALUES(kaggle_url), google_dev_url = VALUES(google_dev_url)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, emptyToNull(data.getProfilePhoto()));
            ps.setString(3, emptyToNull(data.getPhone()));
            ps.setString(4, emptyToNull(data.getDesignation()));
            ps.setString(5, emptyToNull(data.getLocation()));
            ps.setString(6, emptyToNull(data.getAddress()));
            ps.setString(7, emptyToNull(data.getAboutMe()));
            ps.setString(8, emptyToNull(data.getDateOfBirth()));
            ps.setString(9, emptyToNull(data.getGender()));
            ps.setString(10, emptyToNull(data.getLinkedinUrl()));
            ps.setString(11, emptyToNull(data.getGithubUrl()));
            ps.setString(12, emptyToNull(data.getPortfolioUrl()));
            ps.setString(13, emptyToNull(data.getLeetcodeUrl()));
            ps.setString(14, emptyToNull(data.getHackerrankUrl()));
            ps.setString(15, emptyToNull(data.getCodechefUrl()));
            ps.setString(16, emptyToNull(data.getGfgUrl()));
            ps.setString(17, emptyToNull(data.getKaggleUrl()));
            ps.setString(18, emptyToNull(data.getGoogleDevUrl()));
            ps.executeUpdate();
        }
    }

    private void replaceCustomLinks(Connection conn, String userId, List<CustomLink> links) throws SQLException {
        try (PreparedStatement psDel = conn.prepareStatement("DELETE FROM user_custom_links WHERE user_id = ?")) {
            psDel.setString(1, userId);
            psDel.executeUpdate();
        }
        if (links == null || links.isEmpty()) return;

        String ins = "INSERT INTO user_custom_links (id, user_id, title, url, display_order) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement psIns = conn.prepareStatement(ins)) {
            int order = 0;
            for (CustomLink link : links) {
                if (isBlank(link.getTitle()) || isBlank(link.getUrl())) continue;
                psIns.setString(1, isBlank(link.getId()) ? UUID.randomUUID().toString() : link.getId());
                psIns.setString(2, userId);
                psIns.setString(3, link.getTitle().trim());
                psIns.setString(4, link.getUrl().trim());
                psIns.setInt(5, order++);
                psIns.executeUpdate();
            }
        }
    }

    private void saveStudentProfilePart(Connection conn, User actor, UserProfileData data, String cleanFullName, String cleanEmail) throws SQLException {
        Student existing = loadStudentForUser(conn, actor);
        if (existing == null || data.getStudent() == null) return;

        Student st = data.getStudent();
        // Protected fields (roll_number, branch, approval_status, placement_status, placed_company_id, placed_package_lpa) are NEVER updated here
        String sql = "UPDATE students SET user_id = ?, full_name = ?, email = ?, phone = ?, section = ?, " +
                "academic_year = ?, current_semester = ?, graduation_year = ?, cgpa = ?, backlogs = ?, " +
                "tenth_percentage = ?, inter_percentage = ?, resume_url = ?, dob = ?, gender = ?, address = ?, location = ?, about_me = ?, " +
                "technical_skills = ?, programming_languages = ?, frameworks_technologies = ?, soft_skills = ?, " +
                "career_interests = ?, preferred_job_roles = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, actor.getId());
            ps.setString(2, cleanFullName);
            ps.setString(3, cleanEmail);
            ps.setString(4, safeTrim(data.getPhone()));
            ps.setString(5, isBlank(st.getSection()) ? "A" : st.getSection().trim());
            ps.setInt(6, st.getAcademicYear() > 0 ? st.getAcademicYear() : 4);
            ps.setInt(7, st.getCurrentSemester() > 0 ? st.getCurrentSemester() : 7);
            ps.setInt(8, st.getGraduationYear() > 0 ? st.getGraduationYear() : 2026);
            ps.setDouble(9, st.getCgpa());
            ps.setInt(10, Math.max(0, st.getBacklogs()));
            ps.setDouble(11, st.getTenthPercentage());
            ps.setDouble(12, st.getInterPercentage());
            ps.setString(13, emptyToNull(st.getResumeUrl()));
            ps.setString(14, emptyToNull(data.getDateOfBirth()));
            ps.setString(15, emptyToNull(data.getGender()));
            ps.setString(16, emptyToNull(data.getAddress()));
            ps.setString(17, emptyToNull(data.getLocation()));
            ps.setString(18, emptyToNull(data.getAboutMe()));
            ps.setString(19, csvToJsonArray(st.getTechnicalSkills()));
            ps.setString(20, csvToJsonArray(st.getProgrammingLanguages()));
            ps.setString(21, csvToJsonArray(st.getFrameworksTechnologies()));
            ps.setString(22, csvToJsonArray(st.getSoftSkills()));
            ps.setString(23, csvToJsonArray(st.getCareerInterests()));
            ps.setString(24, csvToJsonArray(st.getPreferredJobRoles()));
            ps.setString(25, existing.getId());
            ps.executeUpdate();
        }

        // Replace projects
        try (PreparedStatement psDel = conn.prepareStatement("DELETE FROM student_projects WHERE student_id = ?")) {
            psDel.setString(1, existing.getId());
            psDel.executeUpdate();
        }
        if (data.getProjects() != null) {
            String insProj = "INSERT INTO student_projects (id, student_id, user_id, title, description, technologies, project_url, duration) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insProj)) {
                for (StudentProject p : data.getProjects()) {
                    if (isBlank(p.getTitle())) continue;
                    ps.setString(1, isBlank(p.getId()) ? UUID.randomUUID().toString() : p.getId());
                    ps.setString(2, existing.getId());
                    ps.setString(3, actor.getId());
                    ps.setString(4, p.getTitle().trim());
                    ps.setString(5, safeTrim(p.getDescription()));
                    ps.setString(6, csvToJsonArray(p.getTechnologies()));
                    ps.setString(7, emptyToNull(p.getProjectUrl()));
                    ps.setString(8, emptyToNull(p.getDuration()));
                    ps.executeUpdate();
                }
            }
        }

        // Replace internships
        try (PreparedStatement psDel = conn.prepareStatement("DELETE FROM student_internships WHERE student_id = ?")) {
            psDel.setString(1, existing.getId());
            psDel.executeUpdate();
        }
        if (data.getInternships() != null) {
            String insIntern = "INSERT INTO student_internships (id, student_id, user_id, company_name, role, duration, location, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insIntern)) {
                for (StudentInternship intern : data.getInternships()) {
                    if (isBlank(intern.getCompanyName()) || isBlank(intern.getRole())) continue;
                    ps.setString(1, isBlank(intern.getId()) ? UUID.randomUUID().toString() : intern.getId());
                    ps.setString(2, existing.getId());
                    ps.setString(3, actor.getId());
                    ps.setString(4, intern.getCompanyName().trim());
                    ps.setString(5, intern.getRole().trim());
                    ps.setString(6, safeTrim(intern.getDuration()));
                    ps.setString(7, emptyToNull(intern.getLocation()));
                    ps.setString(8, safeTrim(intern.getDescription()));
                    ps.executeUpdate();
                }
            }
        }

        // Replace certifications
        try (PreparedStatement psDel = conn.prepareStatement("DELETE FROM user_certifications WHERE user_id = ? OR student_id = ?")) {
            psDel.setString(1, actor.getId());
            psDel.setString(2, existing.getId());
            psDel.executeUpdate();
        }
        if (data.getCertifications() != null) {
            String insCert = "INSERT INTO user_certifications (id, user_id, student_id, name, issuing_org, issue_date, credential_id, credential_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insCert)) {
                for (UserCertification cert : data.getCertifications()) {
                    if (isBlank(cert.getName()) || isBlank(cert.getIssuingOrg())) continue;
                    ps.setString(1, isBlank(cert.getId()) ? UUID.randomUUID().toString() : cert.getId());
                    ps.setString(2, actor.getId());
                    ps.setString(3, existing.getId());
                    ps.setString(4, cert.getName().trim());
                    ps.setString(5, cert.getIssuingOrg().trim());
                    ps.setString(6, emptyToNull(cert.getIssueDate()));
                    ps.setString(7, emptyToNull(cert.getCredentialId()));
                    ps.setString(8, emptyToNull(cert.getCredentialUrl()));
                    ps.executeUpdate();
                }
            }
        }

        // Replace achievements
        try (PreparedStatement psDel = conn.prepareStatement("DELETE FROM student_achievements WHERE student_id = ?")) {
            psDel.setString(1, existing.getId());
            psDel.executeUpdate();
        }
        if (data.getAchievements() != null) {
            String insAch = "INSERT INTO student_achievements (id, student_id, user_id, title, category, organizer, event_date, description, credential_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insAch)) {
                for (StudentAchievement ach : data.getAchievements()) {
                    if (isBlank(ach.getTitle())) continue;
                    ps.setString(1, isBlank(ach.getId()) ? UUID.randomUUID().toString() : ach.getId());
                    ps.setString(2, existing.getId());
                    ps.setString(3, actor.getId());
                    ps.setString(4, ach.getTitle().trim());
                    ps.setString(5, isBlank(ach.getCategory()) ? "AWARD" : ach.getCategory().trim().toUpperCase());
                    ps.setString(6, emptyToNull(ach.getOrganizer()));
                    ps.setString(7, emptyToNull(ach.getEventDate()));
                    ps.setString(8, emptyToNull(ach.getDescription()));
                    ps.setString(9, emptyToNull(ach.getCredentialUrl()));
                    ps.executeUpdate();
                }
            }
        }
    }

    private void saveFacultyProfilePart(Connection conn, User actor, UserProfileData data, String cleanFullName, String cleanEmail) throws SQLException {
        Faculty existing = loadFacultyForUser(conn, actor);
        Faculty f = data.getFaculty();
        if (existing != null && f != null) {
            // Protect verified institutional fields: faculty_id, employee_id, department, faculty_approved, admin_approved, status
            String sql = "UPDATE faculty SET user_id = ?, full_name = ?, designation = ?, email = ?, phone = ?, " +
                    "about_me = ?, qualifications = ?, areas_of_expertise = ?, subjects_handled = ?, research_interests = ?, location = ? " +
                    "WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, actor.getId());
                ps.setString(2, cleanFullName);
                ps.setString(3, isBlank(data.getDesignation()) ? existing.getDesignation() : data.getDesignation().trim());
                ps.setString(4, cleanEmail);
                ps.setString(5, emptyToNull(data.getPhone()));
                ps.setString(6, emptyToNull(data.getAboutMe()));
                ps.setString(7, emptyToNull(f.getQualifications()));
                ps.setString(8, csvToJsonArray(f.getAreasOfExpertise()));
                ps.setString(9, csvToJsonArray(f.getSubjectsHandled()));
                ps.setString(10, emptyToNull(f.getResearchInterests()));
                ps.setString(11, emptyToNull(data.getLocation()));
                ps.setString(12, existing.getId());
                ps.executeUpdate();
            }
        }

        // Replace publications
        try (PreparedStatement psDel = conn.prepareStatement("DELETE FROM faculty_publications WHERE user_id = ?")) {
            psDel.setString(1, actor.getId());
            psDel.executeUpdate();
        }
        if (data.getPublications() != null) {
            String insPub = "INSERT INTO faculty_publications (id, faculty_id, user_id, title, journal_or_conference, publication_year, authors, doi_or_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insPub)) {
                for (FacultyPublication pub : data.getPublications()) {
                    if (isBlank(pub.getTitle())) continue;
                    ps.setString(1, isBlank(pub.getId()) ? UUID.randomUUID().toString() : pub.getId());
                    ps.setString(2, existing != null ? existing.getId() : null);
                    ps.setString(3, actor.getId());
                    ps.setString(4, pub.getTitle().trim());
                    ps.setString(5, emptyToNull(pub.getJournalOrConference()));
                    ps.setString(6, emptyToNull(pub.getPublicationYear()));
                    ps.setString(7, emptyToNull(pub.getAuthors()));
                    ps.setString(8, emptyToNull(pub.getDoiOrUrl()));
                    ps.executeUpdate();
                }
            }
        }

        // Replace faculty certifications
        try (PreparedStatement psDel = conn.prepareStatement("DELETE FROM user_certifications WHERE user_id = ? AND student_id IS NULL")) {
            psDel.setString(1, actor.getId());
            psDel.executeUpdate();
        }
        if (data.getCertifications() != null) {
            String insCert = "INSERT INTO user_certifications (id, user_id, student_id, name, issuing_org, issue_date, credential_id, credential_url) VALUES (?, ?, NULL, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insCert)) {
                for (UserCertification cert : data.getCertifications()) {
                    if (isBlank(cert.getName()) || isBlank(cert.getIssuingOrg())) continue;
                    ps.setString(1, isBlank(cert.getId()) ? UUID.randomUUID().toString() : cert.getId());
                    ps.setString(2, actor.getId());
                    ps.setString(3, cert.getName().trim());
                    ps.setString(4, cert.getIssuingOrg().trim());
                    ps.setString(5, emptyToNull(cert.getIssueDate()));
                    ps.setString(6, emptyToNull(cert.getCredentialId()));
                    ps.setString(7, emptyToNull(cert.getCredentialUrl()));
                    ps.executeUpdate();
                }
            }
        }
    }

    private void saveCompanyProfilePart(Connection conn, User actor, UserProfileData data, String cleanFullName, String cleanEmail) throws SQLException {
        Company existing = loadCompanyForUser(conn, actor);
        Company c = data.getCompany();
        if (existing == null || c == null) return;

        // Protect approval_status, tier, approved_by_admin_id
        String sql = "UPDATE companies SET user_id = ?, name = ?, industry = ?, website = ?, location = ?, description = ?, " +
                "hr_name = ?, hr_email = ?, hr_phone = ?, recruiter_name = ?, recruiter_designation = ?, company_logo = ?, linkedin_url = ? " +
                "WHERE id = ?";
        String recruiterName = isBlank(c.getRecruiterName()) ? cleanFullName : c.getRecruiterName().trim();
        String recruiterDesig = isBlank(c.getRecruiterDesignation()) ? safeTrim(data.getDesignation()) : c.getRecruiterDesignation().trim();
        String compLinkedin = isBlank(data.getLinkedinUrl()) ? safeTrim(c.getLinkedinUrl()) : data.getLinkedinUrl().trim();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, actor.getId());
            ps.setString(2, c.getName().trim());
            ps.setString(3, safeTrim(c.getIndustry()));
            ps.setString(4, emptyToNull(c.getWebsite()));
            ps.setString(5, emptyToNull(isBlank(c.getLocation()) ? data.getLocation() : c.getLocation()));
            ps.setString(6, emptyToNull(c.getDescription()));
            ps.setString(7, recruiterName);
            ps.setString(8, cleanEmail);
            ps.setString(9, safeTrim(data.getPhone()));
            ps.setString(10, recruiterName);
            ps.setString(11, emptyToNull(recruiterDesig));
            ps.setString(12, emptyToNull(c.getCompanyLogo()));
            ps.setString(13, emptyToNull(compLinkedin));
            ps.setString(14, existing.getId());
            ps.executeUpdate();
        }
    }

    private void insertAuditLog(Connection conn, User actor, String action, String details) {
        String sql = "INSERT INTO audit_logs (id, action, actor_id, actor_username, actor_role, details) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, action);
            ps.setString(3, actor.getId());
            ps.setString(4, actor.getUsername());
            ps.setString(5, actor.getRole().name());
            ps.setString(6, details);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // =========================================================================
    // STRING & LIST HELPERS
    // =========================================================================

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String emptyToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    public static String parseListToCsv(String raw) {
        if (isBlank(raw)) return "";
        String t = raw.trim();
        if (t.startsWith("[") && t.endsWith("]")) {
            String inner = t.substring(1, t.length() - 1).trim();
            if (inner.isEmpty()) return "";
            String[] tokens = inner.split(",");
            List<String> items = new ArrayList<>();
            for (String tok : tokens) {
                String clean = tok.trim();
                if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() >= 2) {
                    clean = clean.substring(1, clean.length() - 1);
                }
                clean = clean.replace("\\\"", "\"").trim();
                if (!clean.isEmpty()) items.add(clean);
            }
            return String.join(", ", items);
        }
        return t;
    }

    public static String csvToJsonArray(String csv) {
        if (isBlank(csv)) return "[]";
        String[] parts = csv.split(",");
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String p : parts) {
            String item = p.trim();
            if (item.isEmpty()) continue;
            if (!first) sb.append(",");
            sb.append("\"").append(item.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }
}
