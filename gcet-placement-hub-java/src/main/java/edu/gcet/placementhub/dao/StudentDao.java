package edu.gcet.placementhub.dao;

import java.sql.*;
import edu.gcet.placementhub.models.Student;

/**
 * JDBC Data Access Object for the `students` table.
 */
public class StudentDao {

    public Student findByRollNumber(Connection conn, String rollNumber) throws SQLException {
        if (rollNumber == null || rollNumber.isBlank()) return null;
        String sql = "SELECT * FROM students WHERE LOWER(roll_number) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rollNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapStudent(rs);
                }
            }
        }
        return null;
    }

    public Student findByUserId(Connection conn, String userId) throws SQLException {
        if (userId == null || userId.isBlank()) return null;
        String sql = "SELECT * FROM students WHERE user_id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapStudent(rs);
                }
            }
        }
        return null;
    }

    public Student findByEmail(Connection conn, String email) throws SQLException {
        if (email == null || email.isBlank()) return null;
        String sql = "SELECT * FROM students WHERE LOWER(email) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapStudent(rs);
                }
            }
        }
        return null;
    }

    public String findNameByRollNumber(Connection conn, String rollNumber) throws SQLException {
        String sql = "SELECT full_name FROM students WHERE LOWER(roll_number) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rollNumber.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("full_name") : null;
            }
        }
    }

    public Student findByIdOrRoll(Connection conn, String idOrRoll) throws SQLException {
        if (idOrRoll == null || idOrRoll.isBlank()) return null;
        String sql = "SELECT * FROM students WHERE id = ? OR LOWER(roll_number) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idOrRoll.trim());
            ps.setString(2, idOrRoll.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Student s = mapStudent(rs);
                    hydrateSingleStudent(conn, s);
                    return s;
                }
            }
        }
        return null;
    }

    public boolean existsByRollOrEmail(Connection conn, String rollNumber, String email) throws SQLException {
        String sql = "SELECT 1 FROM students WHERE LOWER(roll_number) = LOWER(?) OR LOWER(email) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rollNumber != null ? rollNumber.trim() : "");
            ps.setString(2, email != null ? email.trim() : "");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public java.util.List<Student> findAll(Connection conn) throws SQLException {
        java.util.List<Student> students = new java.util.ArrayList<>();
        String sql = "SELECT * FROM students ORDER BY created_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                students.add(mapStudent(rs));
            }
        }

        if (!students.isEmpty()) {
            java.util.Map<String, java.util.List<edu.gcet.placementhub.models.StudentSkill>> skillMap = loadAllStudentSkills(conn);
            java.util.Map<String, java.util.List<edu.gcet.placementhub.models.StudentProject>> projectMap = loadAllProjects(conn);
            java.util.Map<String, java.util.List<edu.gcet.placementhub.models.StudentCertification>> certMap = loadAllCertifications(conn);
            java.util.Map<String, java.util.List<edu.gcet.placementhub.models.StudentInternship>> internMap = loadAllInternships(conn);
            for (Student s : students) {
                s.setSkills(skillMap.getOrDefault(s.getId(), new java.util.ArrayList<>()));
                s.setProjects(projectMap.getOrDefault(s.getId(), new java.util.ArrayList<>()));
                s.setCertifications(certMap.getOrDefault(s.getId(), new java.util.ArrayList<>()));
                s.setInternships(internMap.getOrDefault(s.getId(), new java.util.ArrayList<>()));
            }
        }
        return students;
    }

    private void hydrateSingleStudent(Connection conn, Student s) throws SQLException {
        if (s == null || s.getId() == null) return;
        s.setSkills(findSkillsForStudent(conn, s.getId()));
        s.setProjects(findProjectsForStudent(conn, s.getId()));
        s.setCertifications(findCertificationsForStudent(conn, s.getId()));
        s.setInternships(findInternshipsForStudent(conn, s.getId()));
    }

    public void insertStudentWithUser(Connection conn, Student s, String passwordHash, String salt) throws SQLException {
        String insertStuSql = """
                INSERT INTO students (
                  id, roll_number, student_id_card, full_name, email, phone, branch,
                  academic_year, year_of_study, cgpa, tenth_percentage, inter_percentage,
                  graduation_year, backlogs, placement_status, approval_status, user_id, resume_url
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'APPROVED', ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(insertStuSql)) {
            ps.setString(1, s.getId());
            ps.setString(2, s.getRollNumber());
            ps.setString(3, s.getStudentIdCard());
            ps.setString(4, s.getFullName());
            ps.setString(5, s.getEmail());
            ps.setString(6, s.getPhone() != null ? s.getPhone() : "");
            ps.setString(7, s.getBranch());
            ps.setInt(8, s.getAcademicYear() > 0 ? s.getAcademicYear() : 4);
            ps.setInt(9, s.getYearOfStudy() > 0 ? s.getYearOfStudy() : 4);
            ps.setDouble(10, s.getCgpa());
            ps.setDouble(11, s.getTenthPercentage());
            ps.setDouble(12, s.getInterPercentage());
            ps.setInt(13, s.getGraduationYear() > 0 ? s.getGraduationYear() : 2026);
            ps.setInt(14, s.getBacklogs());
            ps.setString(15, s.getPlacementStatus() != null ? s.getPlacementStatus() : "UNPLACED");
            ps.setString(16, s.getUserId());
            if (s.getResumeUrl() != null && !s.getResumeUrl().isBlank()) {
                ps.setString(17, s.getResumeUrl().trim());
            } else {
                ps.setNull(17, Types.VARCHAR);
            }
            ps.executeUpdate();
        }

        String insertUserSql = """
                INSERT IGNORE INTO users (
                  id, username, email, full_name, role, department, student_roll_number,
                  password_hash, salt, status, is_active
                ) VALUES (?, ?, ?, ?, 'STUDENT', ?, ?, ?, ?, 'APPROVED', TRUE)
                """;
        try (PreparedStatement ps = conn.prepareStatement(insertUserSql)) {
            ps.setString(1, s.getUserId());
            ps.setString(2, s.getRollNumber());
            ps.setString(3, s.getEmail());
            ps.setString(4, s.getFullName());
            ps.setString(5, s.getBranch());
            ps.setString(6, s.getRollNumber());
            ps.setString(7, passwordHash);
            ps.setString(8, salt);
            ps.executeUpdate();
        }
    }

    public void updateStudent(Connection conn, Student updated, boolean selfStudentOnly) throws SQLException {
        if (selfStudentOnly) {
            String sql = "UPDATE students SET phone = ?, resume_url = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, updated.getPhone() != null ? updated.getPhone().trim() : "");
                if (updated.getResumeUrl() != null && !updated.getResumeUrl().isBlank()) {
                    ps.setString(2, updated.getResumeUrl().trim());
                } else {
                    ps.setNull(2, Types.VARCHAR);
                }
                ps.setString(3, updated.getId());
                ps.executeUpdate();
            }
            return;
        }

        String sql = """
                UPDATE students SET
                  full_name = ?,
                  email = ?,
                  phone = ?,
                  branch = ?,
                  cgpa = ?,
                  tenth_percentage = ?,
                  inter_percentage = ?,
                  graduation_year = ?,
                  backlogs = ?,
                  placement_status = ?,
                  resume_url = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, updated.getFullName());
            ps.setString(2, updated.getEmail());
            ps.setString(3, updated.getPhone() != null ? updated.getPhone().trim() : "");
            ps.setString(4, updated.getBranch());
            ps.setDouble(5, updated.getCgpa());
            ps.setDouble(6, updated.getTenthPercentage());
            ps.setDouble(7, updated.getInterPercentage());
            ps.setInt(8, updated.getGraduationYear());
            ps.setInt(9, updated.getBacklogs());
            ps.setString(10, updated.getPlacementStatus() != null ? updated.getPlacementStatus() : "UNPLACED");
            if (updated.getResumeUrl() != null && !updated.getResumeUrl().isBlank()) {
                ps.setString(11, updated.getResumeUrl().trim());
            } else {
                ps.setNull(11, Types.VARCHAR);
            }
            ps.setString(12, updated.getId());
            ps.executeUpdate();
        }
    }

    public int countApplicationsForStudent(Connection conn, String studentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM applications WHERE student_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void deleteStudentCascade(Connection conn, String studentId, String rollNumber) throws SQLException {
        String[] childDeletes = {
                "DELETE FROM student_skills WHERE student_id = ?",
                "DELETE FROM student_projects WHERE student_id = ?",
                "DELETE FROM student_certifications WHERE student_id = ?",
                "DELETE FROM student_internships WHERE student_id = ?",
                "DELETE FROM students WHERE id = ?"
        };
        for (String sql : childDeletes) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, studentId);
                ps.executeUpdate();
            }
        }
        if (rollNumber != null && !rollNumber.isBlank()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM users WHERE LOWER(student_roll_number) = LOWER(?) AND role = 'STUDENT'")) {
                ps.setString(1, rollNumber.trim());
                ps.executeUpdate();
            }
        }
    }

    public void upsertStudentSkill(
            Connection conn,
            String studentId,
            String skillId,
            int proficiency,
            boolean canVerify,
            String verifierUserId
    ) throws SQLException {
        int clamped = Math.min(5, Math.max(1, proficiency));
        String sql = """
                INSERT INTO student_skills (student_id, skill_id, proficiency, verified_by_faculty, verified_by_faculty_id, verified_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  proficiency = VALUES(proficiency),
                  verified_by_faculty = CASE WHEN ? THEN TRUE ELSE verified_by_faculty END,
                  verified_by_faculty_id = CASE WHEN ? THEN ? ELSE verified_by_faculty_id END,
                  verified_at = CASE WHEN ? THEN NOW() ELSE verified_at END
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            ps.setString(2, skillId);
            ps.setInt(3, clamped);
            ps.setBoolean(4, canVerify);
            if (canVerify && verifierUserId != null) {
                ps.setString(5, verifierUserId);
                ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            } else {
                ps.setNull(5, Types.VARCHAR);
                ps.setNull(6, Types.TIMESTAMP);
            }
            ps.setBoolean(7, canVerify);
            ps.setBoolean(8, canVerify);
            if (verifierUserId != null) {
                ps.setString(9, verifierUserId);
            } else {
                ps.setNull(9, Types.VARCHAR);
            }
            ps.setBoolean(10, canVerify);
            ps.executeUpdate();
        }
    }

    public void verifyStudentSkill(Connection conn, String studentId, String skillId, String verifierUserId) throws SQLException {
        String sql = "UPDATE student_skills SET verified_by_faculty = TRUE, verified_by_faculty_id = ?, verified_at = NOW() WHERE student_id = ? AND skill_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, verifierUserId);
            ps.setString(2, studentId);
            ps.setString(3, skillId);
            ps.executeUpdate();
        }
    }

    public void insertProject(Connection conn, edu.gcet.placementhub.models.StudentProject project) throws SQLException {
        String sql = "INSERT INTO student_projects (id, student_id, title, description, technologies, link, duration) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, project.getId());
            ps.setString(2, project.getStudentId());
            ps.setString(3, project.getTitle());
            ps.setString(4, project.getDescription() != null ? project.getDescription() : "");
            ps.setString(5, toJsonStringArray(project.getTechnologies()));
            if (project.getLink() != null && !project.getLink().isBlank()) {
                ps.setString(6, project.getLink().trim());
            } else {
                ps.setNull(6, Types.VARCHAR);
            }
            if (project.getDuration() != null && !project.getDuration().isBlank()) {
                ps.setString(7, project.getDuration().trim());
            } else {
                ps.setNull(7, Types.VARCHAR);
            }
            ps.executeUpdate();
        }
    }

    public void deleteProject(Connection conn, String projectId, String studentId) throws SQLException {
        String sql = "DELETE FROM student_projects WHERE id = ? AND student_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, studentId);
            ps.executeUpdate();
        }
    }

    public void insertCertification(Connection conn, edu.gcet.placementhub.models.StudentCertification cert) throws SQLException {
        String sql = "INSERT INTO student_certifications (id, student_id, name, issuing_org, issue_date, credential_id) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cert.getId());
            ps.setString(2, cert.getStudentId());
            ps.setString(3, cert.getName());
            ps.setString(4, cert.getIssuingOrg());
            ps.setString(5, cert.getIssueDate());
            if (cert.getCredentialId() != null && !cert.getCredentialId().isBlank()) {
                ps.setString(6, cert.getCredentialId().trim());
            } else {
                ps.setNull(6, Types.VARCHAR);
            }
            ps.executeUpdate();
        }
    }

    public void deleteCertification(Connection conn, String certId, String studentId) throws SQLException {
        String sql = "DELETE FROM student_certifications WHERE id = ? AND student_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, certId);
            ps.setString(2, studentId);
            ps.executeUpdate();
        }
    }

    public void insertInternship(Connection conn, edu.gcet.placementhub.models.StudentInternship internship) throws SQLException {
        String sql = "INSERT INTO student_internships (id, student_id, company_name, role, duration, description) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, internship.getId());
            ps.setString(2, internship.getStudentId());
            ps.setString(3, internship.getCompanyName());
            ps.setString(4, internship.getRole());
            ps.setString(5, internship.getDuration());
            ps.setString(6, internship.getDescription() != null ? internship.getDescription() : "");
            ps.executeUpdate();
        }
    }

    public void deleteInternship(Connection conn, String internshipId, String studentId) throws SQLException {
        String sql = "DELETE FROM student_internships WHERE id = ? AND student_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, internshipId);
            ps.setString(2, studentId);
            ps.executeUpdate();
        }
    }

    public java.util.List<edu.gcet.placementhub.models.StudentSkill> findSkillsForStudent(Connection conn, String studentId) throws SQLException {
        java.util.List<edu.gcet.placementhub.models.StudentSkill> list = new java.util.ArrayList<>();
        if (studentId == null || studentId.isBlank()) return list;
        String sql = "SELECT * FROM student_skills WHERE student_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapStudentSkill(rs));
                }
            }
        }
        return list;
    }

    public java.util.List<edu.gcet.placementhub.models.StudentProject> findProjectsForStudent(Connection conn, String studentId) {
        java.util.List<edu.gcet.placementhub.models.StudentProject> list = new java.util.ArrayList<>();
        if (studentId == null || studentId.isBlank()) return list;
        String sql = "SELECT * FROM student_projects WHERE student_id = ? ORDER BY created_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapProject(rs));
                }
            }
        } catch (SQLException ignored) {
        }
        return list;
    }

    public java.util.List<edu.gcet.placementhub.models.StudentCertification> findCertificationsForStudent(Connection conn, String studentId) {
        java.util.List<edu.gcet.placementhub.models.StudentCertification> list = new java.util.ArrayList<>();
        if (studentId == null || studentId.isBlank()) return list;
        String sql = "SELECT * FROM student_certifications WHERE student_id = ? ORDER BY created_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapCertification(rs));
                }
            }
        } catch (SQLException ignored) {
        }
        return list;
    }

    public java.util.List<edu.gcet.placementhub.models.StudentInternship> findInternshipsForStudent(Connection conn, String studentId) {
        java.util.List<edu.gcet.placementhub.models.StudentInternship> list = new java.util.ArrayList<>();
        if (studentId == null || studentId.isBlank()) return list;
        String sql = "SELECT * FROM student_internships WHERE student_id = ? ORDER BY created_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapInternship(rs));
                }
            }
        } catch (SQLException ignored) {
        }
        return list;
    }

    private java.util.Map<String, java.util.List<edu.gcet.placementhub.models.StudentSkill>> loadAllStudentSkills(Connection conn) {
        java.util.Map<String, java.util.List<edu.gcet.placementhub.models.StudentSkill>> map = new java.util.HashMap<>();
        String sql = "SELECT * FROM student_skills";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                edu.gcet.placementhub.models.StudentSkill sk = mapStudentSkill(rs);
                map.computeIfAbsent(sk.getStudentId(), k -> new java.util.ArrayList<>()).add(sk);
            }
        } catch (SQLException ignored) {
        }
        return map;
    }

    private java.util.Map<String, java.util.List<edu.gcet.placementhub.models.StudentProject>> loadAllProjects(Connection conn) {
        java.util.Map<String, java.util.List<edu.gcet.placementhub.models.StudentProject>> map = new java.util.HashMap<>();
        String sql = "SELECT * FROM student_projects ORDER BY created_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                edu.gcet.placementhub.models.StudentProject p = mapProject(rs);
                map.computeIfAbsent(p.getStudentId(), k -> new java.util.ArrayList<>()).add(p);
            }
        } catch (SQLException ignored) {
        }
        return map;
    }

    private java.util.Map<String, java.util.List<edu.gcet.placementhub.models.StudentCertification>> loadAllCertifications(Connection conn) {
        java.util.Map<String, java.util.List<edu.gcet.placementhub.models.StudentCertification>> map = new java.util.HashMap<>();
        String sql = "SELECT * FROM student_certifications ORDER BY created_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                edu.gcet.placementhub.models.StudentCertification c = mapCertification(rs);
                map.computeIfAbsent(c.getStudentId(), k -> new java.util.ArrayList<>()).add(c);
            }
        } catch (SQLException ignored) {
        }
        return map;
    }

    private java.util.Map<String, java.util.List<edu.gcet.placementhub.models.StudentInternship>> loadAllInternships(Connection conn) {
        java.util.Map<String, java.util.List<edu.gcet.placementhub.models.StudentInternship>> map = new java.util.HashMap<>();
        String sql = "SELECT * FROM student_internships ORDER BY created_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                edu.gcet.placementhub.models.StudentInternship i = mapInternship(rs);
                map.computeIfAbsent(i.getStudentId(), k -> new java.util.ArrayList<>()).add(i);
            }
        } catch (SQLException ignored) {
        }
        return map;
    }

    private edu.gcet.placementhub.models.StudentSkill mapStudentSkill(ResultSet rs) throws SQLException {
        edu.gcet.placementhub.models.StudentSkill sk = new edu.gcet.placementhub.models.StudentSkill();
        sk.setStudentId(safeString(rs, "student_id"));
        sk.setSkillId(safeString(rs, "skill_id"));
        sk.setProficiency(safeInt(rs, "proficiency", 3));
        sk.setVerifiedByFaculty(hasColumn(rs, "verified_by_faculty") && rs.getBoolean("verified_by_faculty"));
        sk.setVerifiedByFacultyId(safeString(rs, "verified_by_faculty_id"));
        Timestamp vAt = safeTimestamp(rs, "verified_at");
        if (vAt != null) sk.setVerifiedAt(vAt.toInstant().toString());
        return sk;
    }

    private edu.gcet.placementhub.models.StudentProject mapProject(ResultSet rs) throws SQLException {
        edu.gcet.placementhub.models.StudentProject p = new edu.gcet.placementhub.models.StudentProject();
        p.setId(safeString(rs, "id"));
        p.setStudentId(safeString(rs, "student_id"));
        p.setTitle(safeString(rs, "title"));
        p.setDescription(safeString(rs, "description"));
        p.setTechnologies(parseJsonStringArray(safeString(rs, "technologies")));
        p.setLink(safeString(rs, "link"));
        p.setDuration(safeString(rs, "duration"));
        Timestamp cAt = safeTimestamp(rs, "created_at");
        if (cAt != null) p.setCreatedAt(cAt.toInstant().toString());
        return p;
    }

    private edu.gcet.placementhub.models.StudentCertification mapCertification(ResultSet rs) throws SQLException {
        edu.gcet.placementhub.models.StudentCertification c = new edu.gcet.placementhub.models.StudentCertification();
        c.setId(safeString(rs, "id"));
        c.setStudentId(safeString(rs, "student_id"));
        c.setName(safeString(rs, "name"));
        c.setIssuingOrg(safeString(rs, "issuing_org"));
        c.setIssueDate(safeString(rs, "issue_date"));
        c.setCredentialId(safeString(rs, "credential_id"));
        Timestamp cAt = safeTimestamp(rs, "created_at");
        if (cAt != null) c.setCreatedAt(cAt.toInstant().toString());
        return c;
    }

    private edu.gcet.placementhub.models.StudentInternship mapInternship(ResultSet rs) throws SQLException {
        edu.gcet.placementhub.models.StudentInternship i = new edu.gcet.placementhub.models.StudentInternship();
        i.setId(safeString(rs, "id"));
        i.setStudentId(safeString(rs, "student_id"));
        i.setCompanyName(safeString(rs, "company_name"));
        i.setRole(safeString(rs, "role"));
        i.setDuration(safeString(rs, "duration"));
        i.setDescription(safeString(rs, "description"));
        Timestamp cAt = safeTimestamp(rs, "created_at");
        if (cAt != null) i.setCreatedAt(cAt.toInstant().toString());
        return i;
    }

    private static java.util.List<String> parseJsonStringArray(String raw) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (raw == null || raw.isBlank()) return list;
        String trimmed = raw.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (inner.isEmpty()) return list;
            StringBuilder current = new StringBuilder();
            boolean inQuotes = false;
            boolean escape = false;
            for (int i = 0; i < inner.length(); i++) {
                char ch = inner.charAt(i);
                if (escape) {
                    current.append(ch);
                    escape = false;
                } else if (ch == '\\') {
                    escape = true;
                } else if (ch == '"') {
                    inQuotes = !inQuotes;
                } else if (ch == ',' && !inQuotes) {
                    String val = current.toString().trim();
                    if (!val.isEmpty()) list.add(val);
                    current.setLength(0);
                } else {
                    current.append(ch);
                }
            }
            String tail = current.toString().trim();
            if (!tail.isEmpty()) list.add(tail);
            return list;
        }
        for (String part : trimmed.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }

    private static String toJsonStringArray(java.util.List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            String val = items.get(i) == null ? "" : items.get(i).replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("\"").append(val).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private Student mapStudent(ResultSet rs) throws SQLException {
        Student s = new Student();
        s.setId(rs.getString("id"));
        s.setRollNumber(rs.getString("roll_number"));
        s.setStudentIdCard(safeString(rs, "student_id_card"));
        s.setFullName(rs.getString("full_name"));
        s.setEmail(rs.getString("email"));
        s.setPhone(safeString(rs, "phone"));
        s.setBranch(rs.getString("branch"));
        s.setYearOfStudy(safeInt(rs, "year_of_study", 4));
        s.setAcademicYear(safeInt(rs, "academic_year", s.getYearOfStudy()));
        s.setSection(safeString(rs, "section"));
        s.setCgpa(rs.getDouble("cgpa"));
        s.setTenthPercentage(safeDouble(rs, "tenth_percentage", 0));
        s.setInterPercentage(safeDouble(rs, "inter_percentage", 0));
        s.setGraduationYear(safeInt(rs, "graduation_year", 2026));
        s.setBacklogs(safeInt(rs, "backlogs", 0));
        s.setPlacementStatus(safeString(rs, "placement_status"));
        s.setApprovalStatus(safeString(rs, "approval_status"));
        s.setApprovedByFacultyId(safeString(rs, "approved_by_faculty_id"));
        s.setApprovedByFacultyName(safeString(rs, "approved_by_faculty_name"));
        s.setRejectionReason(safeString(rs, "rejection_reason"));
        s.setUserId(safeString(rs, "user_id"));
        s.setPlacedCompanyId(safeString(rs, "placed_company_id"));
        double pkg = safeDouble(rs, "placed_package_lpa", -1);
        s.setPlacedPackageLpa(pkg >= 0 ? pkg : null);
        s.setResumeUrl(safeString(rs, "resume_url"));
        Timestamp approvedAt = safeTimestamp(rs, "approved_at");
        if (approvedAt != null) s.setApprovedAt(approvedAt.toInstant().toString());
        Timestamp createdAt = safeTimestamp(rs, "created_at");
        if (createdAt != null) s.setCreatedAt(createdAt.toInstant().toString());
        return s;
    }

    private static boolean hasColumn(ResultSet rs, String column) {
        try {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                if (column.equalsIgnoreCase(meta.getColumnLabel(i))) return true;
            }
        } catch (SQLException ignored) {
        }
        return false;
    }

    private static String safeString(ResultSet rs, String col) throws SQLException {
        return hasColumn(rs, col) ? rs.getString(col) : null;
    }

    private static int safeInt(ResultSet rs, String col, int def) throws SQLException {
        return hasColumn(rs, col) ? rs.getInt(col) : def;
    }

    private static double safeDouble(ResultSet rs, String col, double def) throws SQLException {
        if (!hasColumn(rs, col)) return def;
        Object obj = rs.getObject(col);
        return obj == null ? def : ((Number) obj).doubleValue();
    }

    private static Timestamp safeTimestamp(ResultSet rs, String col) throws SQLException {
        return hasColumn(rs, col) ? rs.getTimestamp(col) : null;
    }
}
