package edu.gcet.placementhub.dao;

import edu.gcet.placementhub.db.DatabaseConnection;
import edu.gcet.placementhub.model.Faculty;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GCET PLACEMENT HUB - Faculty Data Access Object
 * Handles operations for departmental faculty mentors and coordinators.
 */
public class FacultyDao {
    private static final Logger LOGGER = Logger.getLogger(FacultyDao.class.getName());

    public List<Faculty> findAll(String search, String deptFilter, String statusFilter) {
        List<Faculty> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT f.*, u.username as assigned_username " +
            "FROM faculty f " +
            "LEFT JOIN users u ON f.user_id = u.id " +
            "WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(f.faculty_id) LIKE ? OR LOWER(f.full_name) LIKE ? OR LOWER(f.email) LIKE ? OR LOWER(f.department) LIKE ?) ");
            String pat = "%" + search.trim().toLowerCase() + "%";
            params.add(pat);
            params.add(pat);
            params.add(pat);
            params.add(pat);
        }

        if (deptFilter != null && !deptFilter.isEmpty() && !"ALL".equalsIgnoreCase(deptFilter)) {
            sql.append("AND f.department = ? ");
            params.add(deptFilter);
        }

        if (statusFilter != null && !statusFilter.isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
            sql.append("AND f.status = ? ");
            params.add(statusFilter.toUpperCase());
        }

        sql.append("ORDER BY f.department ASC, f.full_name ASC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToFaculty(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error querying faculty records", e);
        }
        return list;
    }

    public Faculty findById(String id) {
        String sql = "SELECT f.*, u.username as assigned_username FROM faculty f LEFT JOIN users u ON f.user_id = u.id WHERE f.id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRowToFaculty(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding faculty by ID: " + id, e);
        }
        return null;
    }

    public boolean isFacultyIdTaken(String facultyId, String excludeId) {
        String sql = "SELECT COUNT(*) FROM faculty WHERE faculty_id = ? AND (? IS NULL OR id != ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, facultyId);
            ps.setString(2, excludeId);
            ps.setString(3, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error checking faculty ID uniqueness", e);
        }
        return false;
    }

    public boolean isEmailTaken(String email, String excludeId) {
        String sql = "SELECT COUNT(*) FROM faculty WHERE email = ? AND (? IS NULL OR id != ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, excludeId);
            ps.setString(3, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error checking faculty email uniqueness", e);
        }
        return false;
    }

    public boolean insert(Faculty faculty) {
        String sql = "INSERT INTO faculty (id, faculty_id, full_name, department, designation, email, phone, user_id, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        if (faculty.getId() == null || faculty.getId().trim().isEmpty()) {
            faculty.setId(UUID.randomUUID().toString());
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, faculty.getId());
            ps.setString(2, faculty.getFacultyId());
            ps.setString(3, faculty.getFullName());
            ps.setString(4, faculty.getDepartment());
            ps.setString(5, faculty.getDesignation());
            ps.setString(6, faculty.getEmail());
            ps.setString(7, faculty.getPhone());
            ps.setString(8, faculty.getUserId());
            ps.setString(9, faculty.getStatus() != null ? faculty.getStatus() : "ACTIVE");

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error inserting faculty", e);
            return false;
        }
    }

    public boolean update(Faculty faculty) {
        String sql = "UPDATE faculty SET full_name = ?, department = ?, designation = ?, email = ?, phone = ?, user_id = ?, status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, faculty.getFullName());
            ps.setString(2, faculty.getDepartment());
            ps.setString(3, faculty.getDesignation());
            ps.setString(4, faculty.getEmail());
            ps.setString(5, faculty.getPhone());
            ps.setString(6, faculty.getUserId());
            ps.setString(7, faculty.getStatus());
            ps.setString(8, faculty.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating faculty ID: " + faculty.getId(), e);
            return false;
        }
    }

    public boolean toggleStatus(String id, String status) {
        String sql = "UPDATE faculty SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.toUpperCase());
            ps.setString(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error toggling faculty status ID: " + id, e);
            return false;
        }
    }

    public boolean delete(String id) {
        String sql = "DELETE FROM faculty WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error deleting faculty ID: " + id, e);
            return false;
        }
    }

    public Map<String, Integer> getMetrics() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("TOTAL", 0);
        map.put("ACTIVE", 0);
        map.put("DEPARTMENTS", 0);

        String sql = "SELECT status, COUNT(*) as cnt, COUNT(DISTINCT department) as dept_cnt FROM faculty GROUP BY status";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            int total = 0;
            int active = 0;
            Set<String> depts = new HashSet<>();

            while (rs.next()) {
                String st = rs.getString("status");
                int count = rs.getInt("cnt");
                total += count;
                if ("ACTIVE".equalsIgnoreCase(st)) {
                    active += count;
                }
            }
            map.put("TOTAL", total);
            map.put("ACTIVE", active);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error getting faculty metrics", e);
        }

        // Count unique departments
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(DISTINCT department) FROM faculty")) {
            if (rs.next()) {
                map.put("DEPARTMENTS", rs.getInt(1));
            }
        } catch (SQLException ignored) {}

        return map;
    }

    public boolean approveByFaculty(String facultyId, String approverId, String approverName) {
        String selectSql = "SELECT admin_approved, user_id FROM faculty WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement psSel = conn.prepareStatement(selectSql)) {
            psSel.setString(1, facultyId);
            try (ResultSet rs = psSel.executeQuery()) {
                if (rs.next()) {
                    boolean adminApproved = rs.getBoolean("admin_approved");
                    String userId = rs.getString("user_id");
                    boolean bothApproved = adminApproved; // Since faculty is approving now

                    String updateSql = "UPDATE faculty SET faculty_approved = TRUE, faculty_approver_id = ?, faculty_approver_name = ?, faculty_approved_at = CURRENT_TIMESTAMP, " +
                                       "status = ? WHERE id = ?";
                    try (PreparedStatement psUpd = conn.prepareStatement(updateSql)) {
                        psUpd.setString(1, approverId);
                        psUpd.setString(2, approverName);
                        psUpd.setString(3, bothApproved ? "ACTIVE" : "PENDING");
                        psUpd.setString(4, facultyId);
                        int affected = psUpd.executeUpdate();

                        if (bothApproved && userId != null) {
                            activateUserAccount(conn, userId);
                        }
                        return affected > 0;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error in approveByFaculty", e);
        }
        return false;
    }

    public boolean approveByAdmin(String facultyId, String approverId, String approverName) {
        String selectSql = "SELECT faculty_approved, user_id FROM faculty WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement psSel = conn.prepareStatement(selectSql)) {
            psSel.setString(1, facultyId);
            try (ResultSet rs = psSel.executeQuery()) {
                if (rs.next()) {
                    boolean facultyApproved = rs.getBoolean("faculty_approved");
                    String userId = rs.getString("user_id");
                    boolean bothApproved = facultyApproved; // Since admin is approving now

                    String updateSql = "UPDATE faculty SET admin_approved = TRUE, admin_approver_id = ?, admin_approver_name = ?, admin_approved_at = CURRENT_TIMESTAMP, " +
                                       "status = ? WHERE id = ?";
                    try (PreparedStatement psUpd = conn.prepareStatement(updateSql)) {
                        psUpd.setString(1, approverId);
                        psUpd.setString(2, approverName);
                        psUpd.setString(3, bothApproved ? "ACTIVE" : "PENDING");
                        psUpd.setString(4, facultyId);
                        int affected = psUpd.executeUpdate();

                        if (bothApproved && userId != null) {
                            activateUserAccount(conn, userId);
                        }
                        return affected > 0;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error in approveByAdmin", e);
        }
        return false;
    }

    public boolean rejectFaculty(String facultyId, String rejecterId, String rejecterName, String reason) {
        String selectSql = "SELECT user_id FROM faculty WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement psSel = conn.prepareStatement(selectSql)) {
            psSel.setString(1, facultyId);
            String userId = null;
            try (ResultSet rs = psSel.executeQuery()) {
                if (rs.next()) userId = rs.getString("user_id");
            }

            String updateSql = "UPDATE faculty SET status = 'REJECTED', rejection_reason = ? WHERE id = ?";
            try (PreparedStatement psUpd = conn.prepareStatement(updateSql)) {
                psUpd.setString(1, reason);
                psUpd.setString(2, facultyId);
                int affected = psUpd.executeUpdate();

                if (userId != null) {
                    try (PreparedStatement psUser = conn.prepareStatement("UPDATE users SET status = 'REJECTED', is_active = FALSE WHERE id = ?")) {
                        psUser.setString(1, userId);
                        psUser.executeUpdate();
                    }
                }
                return affected > 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error rejecting faculty", e);
        }
        return false;
    }

    private void activateUserAccount(Connection conn, String userId) throws SQLException {
        String sql = "UPDATE users SET status = 'ACTIVE', is_active = TRUE WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }
    }

    private Faculty mapRowToFaculty(ResultSet rs) throws SQLException {
        Faculty f = new Faculty();
        f.setId(rs.getString("id"));
        f.setFacultyId(rs.getString("faculty_id"));
        try {
            f.setEmployeeId(rs.getString("employee_id"));
        } catch (SQLException ignored) {}
        f.setFullName(rs.getString("full_name"));
        f.setDepartment(rs.getString("department"));
        f.setDesignation(rs.getString("designation"));
        f.setEmail(rs.getString("email"));
        f.setPhone(rs.getString("phone"));
        f.setUserId(rs.getString("user_id"));
        try {
            f.setUsername(rs.getString("assigned_username"));
        } catch (SQLException ignored) {}
        try {
            f.setFacultyApproved(rs.getBoolean("faculty_approved"));
            f.setFacultyApproverId(rs.getString("faculty_approver_id"));
            f.setFacultyApproverName(rs.getString("faculty_approver_name"));
            f.setFacultyApprovedAt(rs.getTimestamp("faculty_approved_at"));
            f.setAdminApproved(rs.getBoolean("admin_approved"));
            f.setAdminApproverId(rs.getString("admin_approver_id"));
            f.setAdminApproverName(rs.getString("admin_approver_name"));
            f.setAdminApprovedAt(rs.getTimestamp("admin_approved_at"));
            f.setRejectionReason(rs.getString("rejection_reason"));
        } catch (SQLException ignored) {}
        f.setStatus(rs.getString("status"));
        f.setCreatedAt(rs.getTimestamp("created_at"));
        return f;
    }
}
