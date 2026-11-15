package edu.gcet.placementhub.dao;

import edu.gcet.placementhub.database.DatabaseConnection;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.models.UserRole;
import edu.gcet.placementhub.utils.DeptNormalizer;

import java.sql.*;
import java.util.*;

/**
 * JDBC Data Access Object for the `users` table.
 */
public class UserDao {

    public User findByLoginIdentifier(Connection conn, String identifier) throws SQLException {
        String clean = identifier == null ? "" : identifier.trim();
        if (clean.isEmpty()) return null;

        String sql = "SELECT * FROM users WHERE LOWER(username) = LOWER(?) OR LOWER(student_roll_number) = LOWER(?) OR LOWER(email) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clean);
            ps.setString(2, clean);
            ps.setString(3, clean);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        }

        // Secondary fallback: Faculty ID / Employee ID in `users.employee_id` or `faculty` table
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM users WHERE LOWER(employee_id) = LOWER(?) LIMIT 1")) {
            ps.setString(1, clean);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        } catch (SQLException ignored) {
            // Optional column on older schemas
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT u.* FROM users u INNER JOIN faculty f ON f.user_id = u.id "
                        + "WHERE LOWER(f.faculty_id) = LOWER(?) OR LOWER(f.employee_id) = LOWER(?) LIMIT 1")) {
            ps.setString(1, clean);
            ps.setString(2, clean);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        } catch (SQLException ignored) {
        }

        return null;
    }

    public boolean existsByUsername(Connection conn, String username) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public String findUsernameByEmail(Connection conn, String email) throws SQLException {
        String sql = "SELECT username FROM users WHERE LOWER(email) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("username") : null;
            }
        }
    }

    public boolean existsByEmailOrUsername(Connection conn, String email, String username) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE LOWER(email) = LOWER(?) OR LOWER(username) = LOWER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void updateLastLogin(Connection conn, String userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET last_login = NOW() WHERE id = ?")) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }
    }

    public void activateApprovedStudentUser(Connection conn, String userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET is_active = TRUE, status = 'APPROVED' WHERE id = ?")) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }
    }

    public void updatePasswordHash(Connection conn, String userId, String passwordHash, String salt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET password_hash = ?, salt = ?, must_change_password = FALSE WHERE id = ?")) {
            ps.setString(1, passwordHash);
            ps.setString(2, salt);
            ps.setString(3, userId);
            ps.executeUpdate();
        }
    }

    public User findById(Connection conn, String userId) throws SQLException {
        if (userId == null || userId.isBlank()) return null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ? LIMIT 1")) {
            ps.setString(1, userId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        }
    }

    public boolean existsByEmailExcludingUser(Connection conn, String email, String excludeUserId) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE LOWER(email) = LOWER(?) AND id <> ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email != null ? email.trim() : "");
            ps.setString(2, excludeUserId != null ? excludeUserId : "");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void updateUserProfile(
            Connection conn,
            String userId,
            String fullName,
            String email,
            String phone,
            String department,
            String designation
    ) throws SQLException {
        String sql = """
                UPDATE users
                SET full_name = ?, email = ?, phone = ?, department = ?, designation = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, phone != null ? phone : "");
            ps.setString(4, department != null ? department : "");
            ps.setString(5, designation != null ? designation : "");
            ps.setString(6, userId);
            ps.executeUpdate();
        }
    }

    public void createUserAccount(Connection conn, User u, String passwordHash, String salt) throws SQLException {
        String sql = """
                INSERT INTO users (
                  id, username, email, full_name, role, department, designation, phone,
                  password_hash, salt, status, is_active, must_change_password
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', TRUE, FALSE)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, u.getId());
            ps.setString(2, u.getUsername());
            ps.setString(3, u.getEmail());
            ps.setString(4, u.getFullName());
            ps.setString(5, u.getRole().name());
            ps.setString(6, u.getDepartment() != null ? u.getDepartment() : "");
            ps.setString(7, u.getDesignation() != null ? u.getDesignation() : "");
            ps.setString(8, u.getPhone() != null ? u.getPhone() : "");
            ps.setString(9, passwordHash);
            ps.setString(10, salt);
            ps.executeUpdate();
        }
    }

    public int countActiveAdmins(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE UPPER(role) = 'ADMIN' AND (is_active IS NULL OR is_active = TRUE)";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public void deleteUserById(Connection conn, String userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }
    }

    public List<User> findAll() throws SQLException {
        List<User> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM users ORDER BY created_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                User u = mapUser(rs);
                u.setPasswordHash(null);
                u.setSalt(null);
                list.add(u);
            }
        }
        return list;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getString("id"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setFullName(rs.getString("full_name"));
        u.setRole(UserRole.fromString(rs.getString("role")));
        u.setDepartment(safeString(rs, "department"));
        u.setDesignation(safeString(rs, "designation"));
        u.setEmployeeId(safeString(rs, "employee_id"));
        u.setPhone(safeString(rs, "phone"));
        u.setStudentRollNumber(safeString(rs, "student_roll_number"));
        u.setCompanyId(safeString(rs, "company_id"));
        u.setCompanyName(safeString(rs, "company_name"));
        u.setStatus(safeString(rs, "status"));
        u.setMustChangePassword(safeBoolean(rs, "must_change_password", false));
        u.setActive(safeBoolean(rs, "is_active", true));
        u.setPasswordHash(safeString(rs, "password_hash"));
        u.setSalt(safeString(rs, "salt"));
        u.setApprovedBy(safeString(rs, "approved_by"));
        u.setApprovedByName(safeString(rs, "approved_by_name"));
        u.setRejectionReason(safeString(rs, "rejection_reason"));

        Timestamp lastLogin = safeTimestamp(rs, "last_login");
        if (lastLogin != null) u.setLastLogin(lastLogin.toInstant().toString());
        Timestamp createdAt = safeTimestamp(rs, "created_at");
        if (createdAt != null) u.setCreatedAt(createdAt.toInstant().toString());
        Timestamp approvedAt = safeTimestamp(rs, "approved_at");
        if (approvedAt != null) u.setApprovedAt(approvedAt.toInstant().toString());

        String rawAuthDepts = safeString(rs, "authorized_departments");
        List<String> depts = parseDeptArray(rawAuthDepts);
        if (depts.isEmpty()) {
            depts = DeptNormalizer.getFacultyAuthorizedDepts(u.getUsername());
        }
        u.setAuthorizedDepartments(depts);
        return u;
    }

    private List<String> parseDeptArray(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String cleaned = raw.trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        List<String> list = new ArrayList<>();
        for (String part : cleaned.split(",")) {
            String item = part.trim().replace("\"", "").replace("'", "");
            if (!item.isEmpty()) {
                list.add(item);
            }
        }
        return list;
    }

    private static boolean hasColumn(ResultSet rs, String column) {
        try {
            ResultSetMetaData meta = rs.getMetaData();
            int count = meta.getColumnCount();
            for (int i = 1; i <= count; i++) {
                if (column.equalsIgnoreCase(meta.getColumnLabel(i))) {
                    return true;
                }
            }
        } catch (SQLException ignored) {
        }
        return false;
    }

    private static String safeString(ResultSet rs, String col) throws SQLException {
        return hasColumn(rs, col) ? rs.getString(col) : null;
    }

    private static boolean safeBoolean(ResultSet rs, String col, boolean defaultIfNull) throws SQLException {
        if (!hasColumn(rs, col)) return defaultIfNull;
        Object val = rs.getObject(col);
        if (val == null) return defaultIfNull;
        return rs.getBoolean(col);
    }

    private static Timestamp safeTimestamp(ResultSet rs, String col) throws SQLException {
        return hasColumn(rs, col) ? rs.getTimestamp(col) : null;
    }
}
