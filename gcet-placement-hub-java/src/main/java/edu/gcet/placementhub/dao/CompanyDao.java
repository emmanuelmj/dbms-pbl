package edu.gcet.placementhub.dao;

import edu.gcet.placementhub.models.Company;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Data Access Object for `companies`.
 */
public class CompanyDao {

    public List<Company> findAll(Connection conn) throws SQLException {
        List<Company> list = new ArrayList<>();
        String sql = "SELECT * FROM companies ORDER BY created_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapCompany(rs));
            }
        }
        return list;
    }

    public Company findById(Connection conn, String companyId) throws SQLException {
        if (companyId == null || companyId.isBlank()) return null;
        String sql = "SELECT * FROM companies WHERE id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, companyId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapCompany(rs);
                }
            }
        }
        return null;
    }

    public boolean existsByName(Connection conn, String name, String excludeId) throws SQLException {
        String sql = excludeId == null || excludeId.isBlank()
                ? "SELECT 1 FROM companies WHERE LOWER(name) = LOWER(?) LIMIT 1"
                : "SELECT 1 FROM companies WHERE LOWER(name) = LOWER(?) AND id <> ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name != null ? name.trim() : "");
            if (excludeId != null && !excludeId.isBlank()) {
                ps.setString(2, excludeId.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void insertCompany(Connection conn, Company c, String adminId, String adminName) throws SQLException {
        String sql = """
                INSERT INTO companies (
                  id, name, industry, website, location, description,
                  hr_name, hr_email, hr_phone, tier, status, approval_status,
                  approved_by_admin_id, approved_by_name, approved_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """;
        String approval = (c.getApprovalStatus() != null && !c.getApprovalStatus().isBlank())
                ? c.getApprovalStatus()
                : "ACTIVE";
        String status = (c.getStatus() != null && !c.getStatus().isBlank()) ? c.getStatus() : approval;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getId());
            ps.setString(2, c.getName());
            ps.setString(3, c.getIndustry() != null ? c.getIndustry() : "Information Technology");
            ps.setString(4, c.getWebsite() != null ? c.getWebsite() : "");
            ps.setString(5, c.getLocation() != null ? c.getLocation() : "");
            ps.setString(6, c.getDescription() != null ? c.getDescription() : "");
            ps.setString(7, c.getHrName() != null ? c.getHrName() : "");
            ps.setString(8, c.getHrEmail() != null ? c.getHrEmail() : "");
            ps.setString(9, c.getHrPhone() != null ? c.getHrPhone() : "");
            ps.setString(10, c.getTier() != null && !c.getTier().isBlank() ? c.getTier() : "Standard IT");
            ps.setString(11, status);
            ps.setString(12, approval);
            ps.setString(13, adminId);
            ps.setString(14, adminName);
            ps.executeUpdate();
        }
    }

    public void updateCompany(Connection conn, Company c) throws SQLException {
        String sql = """
                UPDATE companies SET
                  name = ?, industry = ?, website = ?, location = ?, description = ?,
                  hr_name = ?, hr_email = ?, hr_phone = ?, tier = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getName());
            ps.setString(2, c.getIndustry() != null ? c.getIndustry() : "Information Technology");
            ps.setString(3, c.getWebsite() != null ? c.getWebsite() : "");
            ps.setString(4, c.getLocation() != null ? c.getLocation() : "");
            ps.setString(5, c.getDescription() != null ? c.getDescription() : "");
            ps.setString(6, c.getHrName() != null ? c.getHrName() : "");
            ps.setString(7, c.getHrEmail() != null ? c.getHrEmail() : "");
            ps.setString(8, c.getHrPhone() != null ? c.getHrPhone() : "");
            ps.setString(9, c.getTier() != null && !c.getTier().isBlank() ? c.getTier() : "Standard IT");
            ps.setString(10, c.getId());
            ps.executeUpdate();
        }
    }

    public void updateApprovalStatus(
            Connection conn,
            String companyId,
            String approvalStatus,
            String reason,
            String adminId,
            String adminName
    ) throws SQLException {
        String sql = """
                UPDATE companies SET
                  status = ?, approval_status = ?, rejection_reason = ?,
                  approved_by_admin_id = ?, approved_by_name = ?, approved_at = NOW()
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, approvalStatus);
            ps.setString(2, approvalStatus);
            ps.setString(3, reason);
            ps.setString(4, adminId);
            ps.setString(5, adminName);
            ps.setString(6, companyId);
            ps.executeUpdate();
        }
    }

    public int countDrivesForCompany(Connection conn, String companyId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM placement_drives WHERE company_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, companyId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void deleteCompany(Connection conn, String companyId) throws SQLException {
        String sql = "DELETE FROM companies WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, companyId);
            ps.executeUpdate();
        }
    }

    private Company mapCompany(ResultSet rs) throws SQLException {
        Company c = new Company();
        c.setId(rs.getString("id"));
        c.setName(rs.getString("name"));
        c.setIndustry(safeString(rs, "industry"));
        c.setWebsite(safeString(rs, "website"));
        c.setLocation(safeString(rs, "location"));
        c.setDescription(safeString(rs, "description"));
        c.setHrName(safeString(rs, "hr_name"));
        c.setHrEmail(safeString(rs, "hr_email"));
        c.setHrPhone(safeString(rs, "hr_phone"));
        String tier = safeString(rs, "tier");
        c.setTier(tier != null && !tier.isBlank() ? tier : "Standard IT");
        String status = safeString(rs, "status");
        String approvalStatus = safeString(rs, "approval_status");
        c.setStatus(status != null && !status.isBlank() ? status : (approvalStatus != null ? approvalStatus : "ACTIVE"));
        c.setApprovalStatus(approvalStatus != null ? approvalStatus : c.getStatus());
        c.setApprovedByAdminId(safeString(rs, "approved_by_admin_id"));
        String adminName = safeString(rs, "approved_by_name");
        if (adminName == null || adminName.isBlank()) {
            adminName = safeString(rs, "approved_by_admin_name");
        }
        c.setApprovedByName(adminName);
        c.setRejectionReason(safeString(rs, "rejection_reason"));
        c.setTotalDrivesConducted(safeInt(rs, "total_drives_conducted", 0));

        Timestamp approvedAt = safeTimestamp(rs, "approved_at");
        if (approvedAt != null) c.setApprovedAt(approvedAt.toInstant().toString());
        Timestamp createdAt = safeTimestamp(rs, "created_at");
        if (createdAt != null) c.setCreatedAt(createdAt.toInstant().toString());
        return c;
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

    private static Timestamp safeTimestamp(ResultSet rs, String col) throws SQLException {
        return hasColumn(rs, col) ? rs.getTimestamp(col) : null;
    }
}
