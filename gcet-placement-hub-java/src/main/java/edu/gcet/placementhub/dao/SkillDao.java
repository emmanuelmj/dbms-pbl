package edu.gcet.placementhub.dao;

import edu.gcet.placementhub.models.Skill;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Data Access Object for the `skills` catalog table.
 */
public class SkillDao {

    public List<Skill> findAllSkills(Connection conn) throws SQLException {
        List<Skill> skills = new ArrayList<>();
        String sql = "SELECT id, name, category, description FROM skills ORDER BY name ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                skills.add(new Skill(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("description")
                ));
            }
        }
        return skills;
    }

    public boolean existsByName(Connection conn, String name) throws SQLException {
        String sql = "SELECT 1 FROM skills WHERE LOWER(name) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name != null ? name.trim() : "");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void insertSkill(Connection conn, Skill skill) throws SQLException {
        String sql = "INSERT INTO skills (id, name, category, description, status) VALUES (?, ?, ?, ?, 'ACTIVE')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, skill.getId());
            ps.setString(2, skill.getName());
            ps.setString(3, skill.getCategory());
            ps.setString(4, skill.getDescription() != null ? skill.getDescription() : "");
            ps.executeUpdate();
        }
    }

    public void updateSkill(Connection conn, Skill skill) throws SQLException {
        String sql = "UPDATE skills SET name = ?, category = ?, description = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, skill.getName());
            ps.setString(2, skill.getCategory());
            ps.setString(3, skill.getDescription() != null ? skill.getDescription() : "");
            ps.setString(4, skill.getId());
            ps.executeUpdate();
        }
    }

    public int countStudentReferences(Connection conn, String skillId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM student_skills WHERE skill_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, skillId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int countDriveReferences(Connection conn, String skillId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM drive_required_skills WHERE skill_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, skillId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void deleteSkill(Connection conn, String skillId) throws SQLException {
        String sql = "DELETE FROM skills WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, skillId);
            ps.executeUpdate();
        }
    }
}
