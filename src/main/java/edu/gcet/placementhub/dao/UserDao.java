package edu.gcet.placementhub.dao;

import edu.gcet.placementhub.auth.PasswordUtil;
import edu.gcet.placementhub.db.DatabaseConnection;
import edu.gcet.placementhub.model.User;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GCET PLACEMENT HUB - User Data Access Object
 * Handles persistent database operations for user accounts and authentication.
 */
public class UserDao {
    private static final Logger LOGGER = Logger.getLogger(UserDao.class.getName());

    public List<User> findAll(String search, String roleFilter, String statusFilter) {
        List<User> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM users WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(username) LIKE ? OR LOWER(full_name) LIKE ? OR LOWER(email) LIKE ? OR LOWER(department) LIKE ?) ");
            String pat = "%" + search.trim().toLowerCase() + "%";
            params.add(pat);
            params.add(pat);
            params.add(pat);
            params.add(pat);
        }

        if (roleFilter != null && !roleFilter.isEmpty() && !"ALL".equalsIgnoreCase(roleFilter)) {
            sql.append("AND role = ? ");
            params.add(roleFilter);
        }

        if (statusFilter != null && !statusFilter.isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
            if ("ACTIVE".equalsIgnoreCase(statusFilter)) {
                sql.append("AND is_active = TRUE ");
            } else if ("INACTIVE".equalsIgnoreCase(statusFilter)) {
                sql.append("AND is_active = FALSE ");
            }
        }

        sql.append("ORDER BY role ASC, full_name ASC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToUser(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error querying users", e);
        }
        return list;
    }

    public List<User> findAdministrators() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE role IN ('PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN', 'ADMIN') AND is_active = TRUE " +
                     "ORDER BY CASE role " +
                     "  WHEN 'PRINCIPAL' THEN 1 " +
                     "  WHEN 'VICE_CHAIRMAN' THEN 2 " +
                     "  WHEN 'CHAIRMAN' THEN 3 " +
                     "  ELSE 4 END, full_name ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToUser(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "MySQL connection note in findAdministrators: " + e.getMessage());
        }

        if (list.isEmpty()) {
            list.add(new User("boot-1", "principal.sagar", "principal@gcet.edu.in", "Dr. K. Sagar", User.Role.PRINCIPAL));
            list.add(new User("boot-2", "vc.harishchandra", "vc@gcet.edu.in", "Mr. R. Harishchandra Reddy", User.Role.VICE_CHAIRMAN));
            list.add(new User("boot-3", "chairman.ravinder", "chairman@gcet.edu.in", "Mr. G.R. Ravinder Reddy", User.Role.CHAIRMAN));
            list.add(new User("boot-4", "admin", "admin@gcet.edu.in", "Placement System Admin", User.Role.ADMIN));
        }
        return list;
    }

    public List<User> findActiveFaculty() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE role = 'FACULTY' AND is_active = TRUE AND status = 'ACTIVE' ORDER BY full_name ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToUser(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "MySQL connection note in findActiveFaculty: " + e.getMessage());
        }

        if (list.isEmpty()) {
            User f1 = new User("fac-boot-1", "faculty_lavanya", "lavanya.cse@gcet.edu.in", "B. Laxmi Lavanya", User.Role.FACULTY);
            f1.setDepartment("CSE");
            list.add(f1);

            User f2 = new User("fac-boot-2", "faculty_aparna", "aparna.ece@gcet.edu.in", "P. Aparna", User.Role.FACULTY);
            f2.setDepartment("ECE");
            list.add(f2);

            User f3 = new User("fac-boot-3", "faculty_rajashakar", "rajashakar.cse@gcet.edu.in", "S. Raja Shakar Reddy", User.Role.FACULTY);
            f3.setDepartment("CSE");
            list.add(f3);
        }
        return list;
    }

    public static class AuthResult {
        public final boolean success;
        public final User user;
        public final String errorMessage;

        public AuthResult(boolean success, User user, String errorMessage) {
            this.success = success;
            this.user = user;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * Strict Administrator Authentication:
     * Verifies that the selected administrator's account exactly matches the provided credentials.
     */
    public AuthResult authenticateAdministrator(String adminIdentifier, String password) {
        if (adminIdentifier == null || adminIdentifier.trim().isEmpty() || password == null || password.isEmpty()) {
            return new AuthResult(false, null, "Please select an administrator and enter password.");
        }

        String sql = "SELECT * FROM users WHERE (LOWER(username) = LOWER(?) OR LOWER(full_name) = LOWER(?)) AND role IN ('PRINCIPAL', 'CHAIRMAN', 'VICE_CHAIRMAN', 'ADMIN')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, adminIdentifier.trim());
            ps.setString(2, adminIdentifier.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    String salt = rs.getString("salt");
                    boolean verified = PasswordUtil.verifyPassword(password, salt, storedHash);

                    if (!verified) {
                        return new AuthResult(false, null, "Incorrect password");
                    }

                    User u = mapRowToUser(rs);
                    if (!u.isActive()) {
                        return new AuthResult(false, null, "Access Denied: This administrative account has been deactivated.");
                    }

                    // Update last login
                    updateLastLogin(u.getId());
                    return new AuthResult(true, u, null);
                }
            }
            return new AuthResult(false, null, "Incorrect username or password. Please try again.");
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Database connection failed during admin authentication", e);
            return new AuthResult(false, null, "Database connection failed: " + e.getMessage());
        }
    }

    /**
     * General Authentication with Approval & Status Verification
     */
    public AuthResult authenticate(String identifier, String password, User.Role expectedRole) {
        if (identifier == null || identifier.trim().isEmpty() || password == null || password.isEmpty()) {
            return new AuthResult(false, null, "Identifier and password are required.");
        }

        String sql = "SELECT * FROM users WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?) OR LOWER(student_roll_number) = LOWER(?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String idVal = identifier.trim();
            ps.setString(1, idVal);
            ps.setString(2, idVal);
            ps.setString(3, idVal);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User u = mapRowToUser(rs);

                    if (expectedRole != null && u.getRole() != expectedRole) {
                        // Allow Principal/Chairman/VC/Admin under Top-Level Admin check
                        if (!(expectedRole == User.Role.ADMIN && u.isTopLevelAdmin())) {
                            return new AuthResult(false, null, "Invalid role portal. This account is registered as " + u.getRole() + ".");
                        }
                    }

                    String storedHash = rs.getString("password_hash");
                    String salt = rs.getString("salt");
                    boolean verified = PasswordUtil.verifyPassword(password, salt, storedHash);

                    if (!verified) {
                        return new AuthResult(false, null, "Incorrect password");
                    }

                    String st = u.getStatus() == null ? "" : u.getStatus().toUpperCase();
                    if ("PENDING".equals(st) || "PENDING_APPROVAL".equals(st)) {
                        return new AuthResult(false, null, "Registration Pending: Your account registration is currently awaiting institutional approval.");
                    }

                    if ("REJECTED".equals(st)) {
                        return new AuthResult(false, null, "Access Denied: Your registration has been rejected by institutional authority.");
                    }

                    if (!u.isActive()) {
                        return new AuthResult(false, null, "Account Inactive: Your account has been suspended by an administrator.");
                    }

                    updateLastLogin(u.getId());
                    return new AuthResult(true, u, null);
                }
            }
            return new AuthResult(false, null, "Incorrect username or password. Please try again.");
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Database connection failed during authentication", e);
            return new AuthResult(false, null, "Database connection failed: " + e.getMessage());
        }
    }

    public void updateLastLogin(String userId) {
        String sql = "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public boolean updateMustChangePassword(String userId, boolean mustChange) {
        String sql = "UPDATE users SET must_change_password = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, mustChange);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error updating must_change_password", e);
            return false;
        }
    }

    public boolean resetPassword(String userId, String newPlaintextPassword) {
        return updatePassword(userId, newPlaintextPassword);
    }

    public boolean isUsernameTaken(String username, String excludeUserId) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ? AND (? IS NULL OR id != ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, excludeUserId);
            ps.setString(3, excludeUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error checking username uniqueness", e);
        }
        return false;
    }

    public boolean isEmailTaken(String email, String excludeUserId) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ? AND (? IS NULL OR id != ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, excludeUserId);
            ps.setString(3, excludeUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error checking email uniqueness", e);
        }
        return false;
    }

    public boolean insert(User user, String plaintextPassword) {
        String sql = "INSERT INTO users (id, username, email, full_name, role, department, student_roll_number, password_hash, salt, is_active) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword(plaintextPassword, salt);

        if (user.getId() == null || user.getId().trim().isEmpty()) {
            user.setId(UUID.randomUUID().toString());
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getFullName());
            ps.setString(5, user.getRole().name());
            ps.setString(6, user.getDepartment());
            ps.setString(7, user.getStudentRollNumber());
            ps.setString(8, hash);
            ps.setString(9, salt);
            ps.setBoolean(10, user.isActive());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error inserting new user", e);
            return false;
        }
    }

    public boolean update(User user) {
        String sql = "UPDATE users SET full_name = ?, email = ?, role = ?, department = ?, student_roll_number = ?, is_active = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getRole().name());
            ps.setString(4, user.getDepartment());
            ps.setString(5, user.getStudentRollNumber());
            ps.setBoolean(6, user.isActive());
            ps.setString(7, user.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating user ID: " + user.getId(), e);
            return false;
        }
    }

    public boolean updatePassword(String userId, String newPlaintextPassword) {
        String sql = "UPDATE users SET password_hash = ?, salt = ? WHERE id = ?";
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword(newPlaintextPassword, salt);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setString(2, salt);
            ps.setString(3, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error resetting password for user ID: " + userId, e);
            return false;
        }
    }

    public boolean toggleStatus(String userId, boolean active) {
        String sql = "UPDATE users SET is_active = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error toggling status for user ID: " + userId, e);
            return false;
        }
    }

    public boolean delete(String userId) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error deleting user ID: " + userId, e);
            return false;
        }
    }

    public Map<String, Integer> getMetrics() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("TOTAL", 0);
        map.put("ACTIVE", 0);
        map.put("ADMIN", 0);
        map.put("PLACEMENT_OFFICER", 0);
        map.put("FACULTY", 0);
        map.put("STUDENT", 0);

        String sql = "SELECT role, is_active, COUNT(*) as cnt FROM users GROUP BY role, is_active";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            int total = 0;
            int active = 0;
            while (rs.next()) {
                String role = rs.getString("role");
                boolean isActive = rs.getBoolean("is_active");
                int count = rs.getInt("cnt");

                total += count;
                if (isActive) active += count;

                map.put(role, map.getOrDefault(role, 0) + count);
            }
            map.put("TOTAL", total);
            map.put("ACTIVE", active);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error getting user metrics", e);
        }
        return map;
    }

    private User mapRowToUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getString("id"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setFullName(rs.getString("full_name"));
        try {
            u.setRole(User.Role.valueOf(rs.getString("role")));
        } catch (Exception e) {
            u.setRole(User.Role.STUDENT);
        }
        u.setDepartment(rs.getString("department"));
        u.setStudentRollNumber(rs.getString("student_roll_number"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setSalt(rs.getString("salt"));
        try {
            u.setStatus(rs.getString("status"));
        } catch (SQLException ignored) {
            u.setStatus("ACTIVE");
        }
        try {
            u.setActive(rs.getBoolean("is_active"));
        } catch (SQLException ignored) {
            u.setActive(true);
        }
        try {
            u.setMustChangePassword(rs.getBoolean("must_change_password"));
        } catch (SQLException ignored) {}
        try {
            u.setCompanyId(rs.getString("company_id"));
        } catch (SQLException ignored) {}
        u.setLastLogin(rs.getTimestamp("last_login"));
        u.setCreatedAt(rs.getTimestamp("created_at"));
        return u;
    }
}
