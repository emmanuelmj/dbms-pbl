package edu.gcet.placementhub.dao;

import edu.gcet.placementhub.db.DatabaseConnection;
import edu.gcet.placementhub.model.Company;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GCET PLACEMENT HUB - Company Data Access Object
 * Handles corporate recruiter records and administrative approval state.
 */
public class CompanyDao {
    private static final Logger LOGGER = Logger.getLogger(CompanyDao.class.getName());

    public List<Company> findAll(String search, String statusFilter) {
        List<Company> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM companies WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(name) LIKE ? OR LOWER(industry) LIKE ? OR LOWER(hr_name) LIKE ? OR LOWER(hr_email) LIKE ?) ");
            String pat = "%" + search.trim().toLowerCase() + "%";
            params.add(pat);
            params.add(pat);
            params.add(pat);
            params.add(pat);
        }

        if (statusFilter != null && !statusFilter.isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
            sql.append("AND approval_status = ? ");
            params.add(statusFilter.toUpperCase());
        }

        sql.append("ORDER BY name ASC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToCompany(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error querying companies", e);
        }
        return list;
    }

    public Company findById(String id) {
        String sql = "SELECT * FROM companies WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRowToCompany(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding company by ID", e);
        }
        return null;
    }

    public Company findByUserId(String userId) {
        String sql = "SELECT * FROM companies WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRowToCompany(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding company by user ID", e);
        }
        return null;
    }

    public boolean isCompanyNameTaken(String name, String excludeId) {
        String sql = "SELECT COUNT(*) FROM companies WHERE LOWER(name) = LOWER(?) AND (? IS NULL OR id != ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            ps.setString(2, excludeId);
            ps.setString(3, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error checking company name uniqueness", e);
        }
        return false;
    }

    public boolean insert(Company c) {
        String sql = "INSERT INTO companies (id, name, industry, website, location, description, hr_name, hr_email, hr_phone, tier, user_id, approval_status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        if (c.getId() == null || c.getId().trim().isEmpty()) {
            c.setId(UUID.randomUUID().toString());
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getId());
            ps.setString(2, c.getName().trim());
            ps.setString(3, c.getIndustry());
            ps.setString(4, c.getWebsite());
            ps.setString(5, c.getLocation());
            ps.setString(6, c.getDescription());
            ps.setString(7, c.getHrName());
            ps.setString(8, c.getHrEmail());
            ps.setString(9, c.getHrPhone());
            ps.setString(10, c.getTier() != null ? c.getTier() : "Standard IT");
            ps.setString(11, c.getUserId());
            ps.setString(12, c.getApprovalStatus() != null ? c.getApprovalStatus() : "ACTIVE");

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error inserting company", e);
            return false;
        }
    }

    public boolean approveCompany(String companyId, String adminId, String adminName) {
        String selectSql = "SELECT name, hr_name, hr_email, user_id FROM companies WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement psSel = conn.prepareStatement(selectSql)) {
            psSel.setString(1, companyId);
            String userId = null;
            String companyName = null;
            String hrName = null;
            String hrEmail = null;
            try (ResultSet rs = psSel.executeQuery()) {
                if (rs.next()) {
                    userId = rs.getString("user_id");
                    companyName = rs.getString("name");
                    hrName = rs.getString("hr_name");
                    hrEmail = rs.getString("hr_email");
                }
            }

            String sql = "UPDATE companies SET approval_status = 'ACTIVE', approved_by_admin_id = ?, " +
                         "approved_by_admin_name = ?, approved_at = CURRENT_TIMESTAMP WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, adminId);
                ps.setString(2, adminName);
                ps.setString(3, companyId);
                int affected = ps.executeUpdate();

                if (userId == null) {
                    try (PreparedStatement psFind = conn.prepareStatement(
                            "SELECT id FROM users WHERE company_id = ? OR LOWER(email) = LOWER(?) LIMIT 1")) {
                        psFind.setString(1, companyId);
                        psFind.setString(2, hrEmail != null ? hrEmail : "");
                        try (ResultSet rs = psFind.executeQuery()) {
                            if (rs.next()) userId = rs.getString("id");
                        }
                    }
                }

                if (userId != null) {
                    try (PreparedStatement psUser = conn.prepareStatement("UPDATE users SET status = 'ACTIVE', is_active = TRUE WHERE id = ?")) {
                        psUser.setString(1, userId);
                        psUser.executeUpdate();
                    }
                } else if (companyName != null) {
                    String initialPass = getConfiguredRecruiterPassword();
                    if (initialPass != null && !initialPass.isEmpty()) {
                        String slug = companyName.toLowerCase().replaceAll("[^a-z0-9]", "");
                        if (slug.length() > 15) slug = slug.substring(0, 15);
                        String recruiterUsername = "recruiter_" + (slug.isEmpty() ? "partner" : slug);
                        String newUserId = "usr-comp-" + UUID.randomUUID();
                        String salt = edu.gcet.placementhub.auth.PasswordUtil.generateSalt();
                        String hash = edu.gcet.placementhub.auth.PasswordUtil.hashPassword(initialPass, salt);
                        String insSql = "INSERT INTO users (id, username, email, full_name, role, department, company_id, password_hash, salt, must_change_password, status, is_active, approved_by, approved_at) " +
                                        "VALUES (?, ?, ?, ?, 'COMPANY', ?, ?, ?, ?, TRUE, 'APPROVED', TRUE, ?, CURRENT_TIMESTAMP)";
                        try (PreparedStatement psIns = conn.prepareStatement(insSql)) {
                            psIns.setString(1, newUserId);
                            psIns.setString(2, recruiterUsername);
                            psIns.setString(3, hrEmail != null && !hrEmail.isBlank() ? hrEmail.trim() : recruiterUsername + "@company.com");
                            psIns.setString(4, (hrName != null && !hrName.isBlank() ? hrName.trim() : "HR Lead") + " (" + companyName + ")");
                            psIns.setString(5, companyName);
                            psIns.setString(6, companyId);
                            psIns.setString(7, hash);
                            psIns.setString(8, salt);
                            psIns.setString(9, adminName);
                            psIns.executeUpdate();
                        }
                        try (PreparedStatement psLink = conn.prepareStatement("UPDATE companies SET user_id = ? WHERE id = ?")) {
                            psLink.setString(1, newUserId);
                            psLink.setString(2, companyId);
                            psLink.executeUpdate();
                        }
                    }
                }
                return affected > 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error approving company", e);
            return false;
        }
    }

    private String getConfiguredRecruiterPassword() {
        String v = System.getenv("GCET_BOOTSTRAP_RECRUITER_PASS");
        if (v != null && !v.trim().isEmpty()) return v.trim();
        try {
            java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
            if (java.nio.file.Files.isRegularFile(envPath)) {
                for (String raw : java.nio.file.Files.readAllLines(envPath, java.nio.charset.StandardCharsets.UTF_8)) {
                    String line = raw.trim();
                    if (!line.startsWith("GCET_BOOTSTRAP_RECRUITER_PASS=")) continue;
                    String val = line.substring("GCET_BOOTSTRAP_RECRUITER_PASS=".length()).trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        if (val.length() >= 2) val = val.substring(1, val.length() - 1);
                    }
                    if (!val.trim().isEmpty()) return val.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public boolean rejectCompany(String companyId, String adminId, String adminName, String reason) {
        String selectSql = "SELECT user_id FROM companies WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement psSel = conn.prepareStatement(selectSql)) {
            psSel.setString(1, companyId);
            String userId = null;
            try (ResultSet rs = psSel.executeQuery()) {
                if (rs.next()) userId = rs.getString("user_id");
            }

            String sql = "UPDATE companies SET approval_status = 'REJECTED', approved_by_admin_id = ?, " +
                         "approved_by_admin_name = ?, approved_at = CURRENT_TIMESTAMP, rejection_reason = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, adminId);
                ps.setString(2, adminName);
                ps.setString(3, reason);
                ps.setString(4, companyId);
                int affected = ps.executeUpdate();

                if (userId != null) {
                    try (PreparedStatement psUser = conn.prepareStatement("UPDATE users SET status = 'REJECTED', is_active = FALSE WHERE id = ?")) {
                        psUser.setString(1, userId);
                        psUser.executeUpdate();
                    }
                }
                return affected > 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error rejecting company", e);
            return false;
        }
    }

    private Company mapRowToCompany(ResultSet rs) throws SQLException {
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
            c.setApprovedByAdminId(rs.getString("approved_by_admin_id"));
            c.setApprovedByAdminName(rs.getString("approved_by_admin_name"));
            c.setApprovedAt(rs.getTimestamp("approved_at"));
            c.setRejectionReason(rs.getString("rejection_reason"));
        } catch (SQLException ignored) {}
        c.setCreatedAt(rs.getTimestamp("created_at"));
        return c;
    }
}
