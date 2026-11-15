package edu.gcet.placementhub.dao;

import edu.gcet.placementhub.db.DatabaseConnection;
import edu.gcet.placementhub.model.Student;
import edu.gcet.placementhub.model.StudentSkill;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GCET PLACEMENT HUB - Student Data Access Object
 * Provides robust, transaction-safe, prepared-statement operations for student records.
 */
public class StudentDao {
    private static final Logger LOGGER = Logger.getLogger(StudentDao.class.getName());

    public List<Student> findAll() throws SQLException {
        return findWithFilters(null, null, null);
    }

    public List<Student> findWithFilters(String branchFilter, Integer gradYearFilter, String searchQuery) throws SQLException {
        List<Student> students = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM students WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (branchFilter != null && !branchFilter.trim().isEmpty() && !branchFilter.equalsIgnoreCase("ALL")) {
            sql.append("AND branch = ? ");
            params.add(branchFilter.trim());
        }

        if (gradYearFilter != null && gradYearFilter > 0) {
            sql.append("AND graduation_year = ? ");
            params.add(gradYearFilter);
        }

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            sql.append("AND (LOWER(full_name) LIKE ? OR LOWER(roll_number) LIKE ? OR LOWER(email) LIKE ?) ");
            String queryParam = "%" + searchQuery.trim().toLowerCase() + "%";
            params.add(queryParam);
            params.add(queryParam);
            params.add(queryParam);
        }

        sql.append("ORDER BY roll_number ASC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    students.add(mapResultSetToStudent(rs));
                }
            }
        }
        return students;
    }

    public Optional<Student> findById(String id) throws SQLException {
        String sql = "SELECT * FROM students WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Student s = mapResultSetToStudent(rs);
                    s.setSkills(loadStudentSkills(conn, s.getId()));
                    return Optional.of(s);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Student> findByRollNumber(String rollNumber) throws SQLException {
        String sql = "SELECT * FROM students WHERE LOWER(roll_number) = LOWER(?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rollNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Student s = mapResultSetToStudent(rs);
                    s.setSkills(loadStudentSkills(conn, s.getId()));
                    return Optional.of(s);
                }
            }
        }
        return Optional.empty();
    }

    public boolean isRollNumberTaken(String rollNumber, String excludeStudentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM students WHERE LOWER(roll_number) = LOWER(?)";
        if (excludeStudentId != null && !excludeStudentId.trim().isEmpty()) {
            sql += " AND id != ?";
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rollNumber.trim());
            if (excludeStudentId != null && !excludeStudentId.trim().isEmpty()) {
                ps.setString(2, excludeStudentId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    public boolean isEmailTaken(String email, String excludeStudentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM students WHERE LOWER(email) = LOWER(?)";
        if (excludeStudentId != null && !excludeStudentId.trim().isEmpty()) {
            sql += " AND id != ?";
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            if (excludeStudentId != null && !excludeStudentId.trim().isEmpty()) {
                ps.setString(2, excludeStudentId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    public void insert(Student s) throws SQLException {
        if (s.getId() == null || s.getId().trim().isEmpty()) {
            s.setId(UUID.randomUUID().toString());
        }

        String sql = "INSERT INTO students (id, roll_number, full_name, email, phone, branch, " +
                     "cgpa, tenth_percentage, inter_percentage, graduation_year, backlogs, placement_status, resume_url) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.getId());
            ps.setString(2, s.getRollNumber().trim().toUpperCase());
            ps.setString(3, s.getFullName().trim());
            ps.setString(4, s.getEmail().trim().toLowerCase());
            ps.setString(5, s.getPhone());
            ps.setString(6, s.getBranch());
            ps.setDouble(7, s.getCgpa());
            ps.setDouble(8, s.getTenthPercentage());
            ps.setDouble(9, s.getInterPercentage());
            ps.setInt(10, s.getGraduationYear());
            ps.setInt(11, s.getBacklogs());
            ps.setString(12, s.getPlacementStatus() != null ? s.getPlacementStatus() : "UNPLACED");
            ps.setString(13, s.getResumeUrl());
            ps.executeUpdate();
        }
    }

    public void update(Student s) throws SQLException {
        String sql = "UPDATE students SET roll_number = ?, full_name = ?, email = ?, phone = ?, " +
                     "branch = ?, cgpa = ?, tenth_percentage = ?, inter_percentage = ?, graduation_year = ?, " +
                     "backlogs = ?, placement_status = ?, resume_url = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.getRollNumber().trim().toUpperCase());
            ps.setString(2, s.getFullName().trim());
            ps.setString(3, s.getEmail().trim().toLowerCase());
            ps.setString(4, s.getPhone());
            ps.setString(5, s.getBranch());
            ps.setDouble(6, s.getCgpa());
            ps.setDouble(7, s.getTenthPercentage());
            ps.setDouble(8, s.getInterPercentage());
            ps.setInt(9, s.getGraduationYear());
            ps.setInt(10, s.getBacklogs());
            ps.setString(11, s.getPlacementStatus());
            ps.setString(12, s.getResumeUrl());
            ps.setString(13, s.getId());

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Student record not found for update: " + s.getId());
            }
        }
    }

    /**
     * Checks if a student can be deleted safely or has associated active placement applications.
     * @return reason string if blocked, null if safe to delete
     */
    public String checkDeletionBlockers(String studentId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String appSql = "SELECT COUNT(*) FROM applications WHERE student_id = ? AND current_stage IN ('SHORTLISTED', 'TECHNICAL_INTERVIEW', 'HR_INTERVIEW', 'SELECTED', 'OFFERED', 'JOINED')";
            try (PreparedStatement ps = conn.prepareStatement(appSql)) {
                ps.setString(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return "Student has " + rs.getInt(1) + " active/advanced recruitment applications in progress.";
                    }
                }
            }
        }
        return null; // Safe to delete
    }

    public void delete(String studentId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete dependent student skills
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM student_skills WHERE student_id = ?")) {
                    ps.setString(1, studentId);
                    ps.executeUpdate();
                }

                // Delete applications
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM applications WHERE student_id = ?")) {
                    ps.setString(1, studentId);
                    ps.executeUpdate();
                }

                // Delete student
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM students WHERE id = ?")) {
                    ps.setString(1, studentId);
                    int rows = ps.executeUpdate();
                    if (rows == 0) {
                        throw new SQLException("Student not found for deletion with ID: " + studentId);
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // ==========================================
    // METRICS FOR STUDENT MANAGEMENT DASHBOARD
    // ==========================================

    public int getTotalStudentsCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM students";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public double getAverageCgpa() throws SQLException {
        String sql = "SELECT AVG(cgpa) FROM students";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getDouble(1);
        }
        return 0.0;
    }

    public Map<String, Integer> getBranchWiseCounts() throws SQLException {
        Map<String, Integer> map = new LinkedHashMap<>();
        String sql = "SELECT branch, COUNT(*) FROM students GROUP BY branch ORDER BY branch ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getInt(2));
            }
        }
        return map;
    }

    public Map<Integer, Integer> getGraduationYearDistribution() throws SQLException {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        String sql = "SELECT graduation_year, COUNT(*) FROM students GROUP BY graduation_year ORDER BY graduation_year ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getInt(1), rs.getInt(2));
            }
        }
        return map;
    }

    public List<Student> getRecentlyRegisteredStudents(int limit) throws SQLException {
        List<Student> list = new ArrayList<>();
        String sql = "SELECT * FROM students ORDER BY created_at DESC LIMIT ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToStudent(rs));
                }
            }
        }
        return list;
    }

    public boolean isStudentIdCardTaken(String studentIdCard, String excludeId) throws SQLException {
        if (studentIdCard == null || studentIdCard.trim().isEmpty()) return false;
        String sql = "SELECT COUNT(*) FROM students WHERE LOWER(student_id_card) = LOWER(?)";
        if (excludeId != null && !excludeId.trim().isEmpty()) {
            sql += " AND id != ?";
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentIdCard.trim());
            if (excludeId != null && !excludeId.trim().isEmpty()) {
                ps.setString(2, excludeId.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    public boolean approveStudent(String studentId, String facultyId, String facultyName) throws SQLException {
        String selectSql = "SELECT user_id FROM students WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement psSel = conn.prepareStatement(selectSql)) {
            psSel.setString(1, studentId);
            String userId = null;
            try (ResultSet rs = psSel.executeQuery()) {
                if (rs.next()) userId = rs.getString("user_id");
            }

            String sql = "UPDATE students SET approval_status = 'ACTIVE', approved_by_faculty_id = ?, " +
                         "approved_by_faculty_name = ?, approved_at = CURRENT_TIMESTAMP WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, facultyId);
                ps.setString(2, facultyName);
                ps.setString(3, studentId);
                int affected = ps.executeUpdate();

                if (userId != null) {
                    try (PreparedStatement psUser = conn.prepareStatement("UPDATE users SET status = 'ACTIVE', is_active = TRUE WHERE id = ?")) {
                        psUser.setString(1, userId);
                        psUser.executeUpdate();
                    }
                }
                return affected > 0;
            }
        }
    }

    public boolean rejectStudent(String studentId, String facultyId, String facultyName, String reason) throws SQLException {
        String selectSql = "SELECT user_id FROM students WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement psSel = conn.prepareStatement(selectSql)) {
            psSel.setString(1, studentId);
            String userId = null;
            try (ResultSet rs = psSel.executeQuery()) {
                if (rs.next()) userId = rs.getString("user_id");
            }

            String sql = "UPDATE students SET approval_status = 'REJECTED', approved_by_faculty_id = ?, " +
                         "approved_by_faculty_name = ?, approved_at = CURRENT_TIMESTAMP, rejection_reason = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, facultyId);
                ps.setString(2, facultyName);
                ps.setString(3, reason);
                ps.setString(4, studentId);
                int affected = ps.executeUpdate();

                if (userId != null) {
                    try (PreparedStatement psUser = conn.prepareStatement("UPDATE users SET status = 'REJECTED', is_active = FALSE WHERE id = ?")) {
                        psUser.setString(1, userId);
                        psUser.executeUpdate();
                    }
                }
                return affected > 0;
            }
        }
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    public List<StudentSkill> loadStudentSkills(Connection conn, String studentId) throws SQLException {
        List<StudentSkill> list = new ArrayList<>();
        String sql = "SELECT ss.*, s.name as skill_name, s.category as skill_category " +
                     "FROM student_skills ss " +
                     "JOIN skills s ON ss.skill_id = s.id " +
                     "WHERE ss.student_id = ? " +
                     "ORDER BY s.category ASC, s.name ASC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StudentSkill sk = new StudentSkill();
                    sk.setStudentId(rs.getString("student_id"));
                    sk.setSkillId(rs.getString("skill_id"));
                    sk.setSkillName(rs.getString("skill_name"));
                    sk.setSkillCategory(rs.getString("skill_category"));
                    sk.setProficiency(rs.getInt("proficiency"));
                    sk.setVerifiedByFaculty(rs.getBoolean("verified_by_faculty"));
                    sk.setVerifiedByFacultyId(rs.getString("verified_by_faculty_id"));
                    sk.setVerifiedAt(rs.getTimestamp("verified_at"));
                    list.add(sk);
                }
            }
        }
        return list;
    }

    private Student mapResultSetToStudent(ResultSet rs) throws SQLException {
        Student s = new Student();
        s.setId(rs.getString("id"));
        s.setRollNumber(rs.getString("roll_number"));
        try {
            s.setStudentIdCard(rs.getString("student_id_card"));
            s.setYearOfStudy(rs.getInt("year_of_study"));
            s.setSection(rs.getString("section"));
            s.setApprovalStatus(rs.getString("approval_status"));
            s.setApprovedByFacultyId(rs.getString("approved_by_faculty_id"));
            s.setApprovedByFacultyName(rs.getString("approved_by_faculty_name"));
            s.setApprovedAt(rs.getTimestamp("approved_at"));
            s.setRejectionReason(rs.getString("rejection_reason"));
            s.setUserId(rs.getString("user_id"));
        } catch (SQLException ignored) {}
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
        s.setPlacedCompanyId(rs.getString("placed_company_id"));
        Double pkg = rs.getDouble("placed_package_lpa");
        if (!rs.wasNull()) {
            s.setPlacedPackageLpa(pkg);
        }
        s.setResumeUrl(rs.getString("resume_url"));
        s.setCreatedAt(rs.getTimestamp("created_at"));
        try {
            int sem = rs.getInt("current_semester");
            if (!rs.wasNull() && sem > 0) s.setCurrentSemester(sem);
            int ay = rs.getInt("academic_year");
            if (!rs.wasNull() && ay > 0) s.setAcademicYear(ay);
            s.setDob(rs.getString("dob"));
            s.setGender(rs.getString("gender"));
            s.setAddress(rs.getString("address"));
            s.setLocation(rs.getString("location"));
            s.setAboutMe(rs.getString("about_me"));
            s.setTechnicalSkills(rs.getString("technical_skills"));
            s.setProgrammingLanguages(rs.getString("programming_languages"));
            s.setFrameworksTechnologies(rs.getString("frameworks_technologies"));
            s.setSoftSkills(rs.getString("soft_skills"));
            s.setCareerInterests(rs.getString("career_interests"));
            s.setPreferredJobRoles(rs.getString("preferred_job_roles"));
        } catch (SQLException ignored) {}
        return s;
    }
}
