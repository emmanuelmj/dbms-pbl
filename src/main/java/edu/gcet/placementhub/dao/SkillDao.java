package edu.gcet.placementhub.dao;

import edu.gcet.placementhub.db.DatabaseConnection;
import edu.gcet.placementhub.model.Skill;
import edu.gcet.placementhub.model.StudentSkill;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GCET PLACEMENT HUB - Skill Matrix Data Access Object
 * Manages the GCET technical competency catalog, student skill ratings,
 * and multi-dimensional matrix queries.
 */
public class SkillDao {
    private static final Logger LOGGER = Logger.getLogger(SkillDao.class.getName());

    // ========================================================
    // 1. Skill Library Operations
    // ========================================================

    public List<Skill> findAllSkills(String search, String category, String status) {
        List<Skill> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM skills WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(name) LIKE ? OR LOWER(description) LIKE ?) ");
            String pat = "%" + search.trim().toLowerCase() + "%";
            params.add(pat);
            params.add(pat);
        }

        if (category != null && !category.isEmpty() && !"ALL".equalsIgnoreCase(category)) {
            sql.append("AND category = ? ");
            params.add(category);
        }

        if (status != null && !status.isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            sql.append("AND status = ? ");
            params.add(status.toUpperCase());
        }

        sql.append("ORDER BY category ASC, name ASC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToSkill(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error querying skills library", e);
        }
        return list;
    }

    public Skill findSkillById(String id) {
        String sql = "SELECT * FROM skills WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRowToSkill(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding skill by ID: " + id, e);
        }
        return null;
    }

    public boolean isSkillNameTaken(String name, String excludeId) {
        String sql = "SELECT COUNT(*) FROM skills WHERE LOWER(name) = LOWER(?) AND (? IS NULL OR id != ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            ps.setString(2, excludeId);
            ps.setString(3, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error checking skill name uniqueness", e);
        }
        return false;
    }

    public boolean insertSkill(Skill skill) {
        String sql = "INSERT INTO skills (id, name, category, description, status) VALUES (?, ?, ?, ?, ?)";
        if (skill.getId() == null || skill.getId().trim().isEmpty()) {
            skill.setId(UUID.randomUUID().toString());
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, skill.getId());
            ps.setString(2, skill.getName().trim());
            ps.setString(3, skill.getCategory().trim());
            ps.setString(4, skill.getDescription());
            ps.setString(5, skill.getStatus() != null ? skill.getStatus() : "ACTIVE");

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error inserting skill", e);
            return false;
        }
    }

    public boolean updateSkill(Skill skill) {
        String sql = "UPDATE skills SET name = ?, category = ?, description = ?, status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, skill.getName().trim());
            ps.setString(2, skill.getCategory().trim());
            ps.setString(3, skill.getDescription());
            ps.setString(4, skill.getStatus());
            ps.setString(5, skill.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating skill ID: " + skill.getId(), e);
            return false;
        }
    }

    public boolean toggleSkillStatus(String id, String status) {
        String sql = "UPDATE skills SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.toUpperCase());
            ps.setString(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error toggling skill status ID: " + id, e);
            return false;
        }
    }

    public boolean deleteSkill(String id) {
        String sql = "DELETE FROM skills WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error deleting skill ID: " + id, e);
            return false;
        }
    }

    public List<String> getSkillCategories() {
        List<String> categories = new ArrayList<>();
        String sql = "SELECT DISTINCT category FROM skills WHERE category IS NOT NULL ORDER BY category ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                categories.add(rs.getString("category"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error loading skill categories", e);
        }
        return categories;
    }

    // ========================================================
    // 2. Student Skill Operations
    // ========================================================

    public List<StudentSkill> findSkillsByStudentId(String studentId) {
        List<StudentSkill> list = new ArrayList<>();
        String sql = "SELECT ss.*, sk.name as skill_name, sk.category as skill_category, u.full_name as verifier_name " +
                     "FROM student_skills ss " +
                     "JOIN skills sk ON ss.skill_id = sk.id " +
                     "LEFT JOIN users u ON ss.verified_by_faculty_id = u.id " +
                     "WHERE ss.student_id = ? " +
                     "ORDER BY sk.category ASC, sk.name ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StudentSkill ss = new StudentSkill();
                    ss.setStudentId(rs.getString("student_id"));
                    ss.setSkillId(rs.getString("skill_id"));
                    ss.setSkillName(rs.getString("skill_name"));
                    ss.setSkillCategory(rs.getString("skill_category"));
                    ss.setProficiency(rs.getInt("proficiency"));
                    try {
                        ss.setExperienceDuration(rs.getString("experience_duration"));
                    } catch (SQLException ignored) {}
                    try {
                        ss.setCertificationEvidence(rs.getString("certification_evidence"));
                    } catch (SQLException ignored) {}
                    ss.setVerifiedByFaculty(rs.getBoolean("verified_by_faculty"));
                    ss.setVerifiedByFacultyId(rs.getString("verified_by_faculty_id"));
                    ss.setVerifierName(rs.getString("verifier_name"));
                    ss.setVerifiedAt(rs.getTimestamp("verified_at"));
                    list.add(ss);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error loading student skills for student: " + studentId, e);
        }
        return list;
    }

    public boolean addOrUpdateStudentSkill(StudentSkill ss) {
        String sql = "INSERT INTO student_skills (student_id, skill_id, proficiency, experience_duration, certification_evidence, verified_by_faculty, verified_by_faculty_id, verified_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE " +
                     "proficiency = VALUES(proficiency), " +
                     "experience_duration = VALUES(experience_duration), " +
                     "certification_evidence = VALUES(certification_evidence), " +
                     "verified_by_faculty = VALUES(verified_by_faculty), " +
                     "verified_by_faculty_id = VALUES(verified_by_faculty_id), " +
                     "verified_at = VALUES(verified_at)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ss.getStudentId());
            ps.setString(2, ss.getSkillId());
            ps.setInt(3, ss.getProficiency());
            ps.setString(4, ss.getExperienceDuration() != null ? ss.getExperienceDuration() : "6 months");
            ps.setString(5, ss.getCertificationEvidence());
            ps.setBoolean(6, ss.isVerifiedByFaculty());
            ps.setString(7, ss.getVerifiedByFacultyId());
            ps.setTimestamp(8, ss.getVerifiedAt());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error upserting student skill", e);
            return false;
        }
    }

    public boolean removeStudentSkill(String studentId, String skillId) {
        String sql = "DELETE FROM student_skills WHERE student_id = ? AND skill_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            ps.setString(2, skillId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error removing student skill", e);
            return false;
        }
    }

    public boolean verifyStudentSkill(String studentId, String skillId, String facultyUserId) {
        String sql = "UPDATE student_skills SET verified_by_faculty = TRUE, verified_by_faculty_id = ?, verified_at = CURRENT_TIMESTAMP WHERE student_id = ? AND skill_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, facultyUserId);
            ps.setString(2, studentId);
            ps.setString(3, skillId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error verifying student skill", e);
            return false;
        }
    }

    // ========================================================
    // 3. Matrix & Metric Aggregations
    // ========================================================

    public Map<String, Integer> getSkillMetrics() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("TOTAL_SKILLS", 0);
        map.put("ACTIVE_SKILLS", 0);
        map.put("CATEGORIES", 0);
        map.put("VERIFIED_ASSESSMENTS", 0);

        try (Connection conn = DatabaseConnection.getConnection()) {
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT status, COUNT(*) as cnt FROM skills GROUP BY status")) {
                int total = 0;
                int active = 0;
                while (rs.next()) {
                    int c = rs.getInt("cnt");
                    total += c;
                    if ("ACTIVE".equalsIgnoreCase(rs.getString("status"))) active += c;
                }
                map.put("TOTAL_SKILLS", total);
                map.put("ACTIVE_SKILLS", active);
            }

            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(DISTINCT category) FROM skills")) {
                if (rs.next()) map.put("CATEGORIES", rs.getInt(1));
            }

            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM student_skills WHERE verified_by_faculty = TRUE")) {
                if (rs.next()) map.put("VERIFIED_ASSESSMENTS", rs.getInt(1));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error getting skill metrics", e);
        }
        return map;
    }

    private Skill mapRowToSkill(ResultSet rs) throws SQLException {
        Skill s = new Skill();
        s.setId(rs.getString("id"));
        s.setName(rs.getString("name"));
        s.setCategory(rs.getString("category"));
        s.setDescription(rs.getString("description"));
        try {
            s.setStatus(rs.getString("status"));
        } catch (SQLException ignored) {
            s.setStatus("ACTIVE");
        }
        s.setCreatedAt(rs.getTimestamp("created_at"));
        return s;
    }
}
